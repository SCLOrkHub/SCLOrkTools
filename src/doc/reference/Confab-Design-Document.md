# Confab Design Document {#Confab-Design-Document}

[TOC]

The confab program is the C++-based support binary for the SCLOrkTools resource sharing system that powers things like
chat, images, memes, and code sharing in SCLOrkChat.

This document serves as an overview of the design of the confab program.

# Overview

# Architecture

Confab is based on a classical client-server model, with the added complication that since the SuperCollider language
can't communicate over HTTP there is a *proxy* client, written in C++, that caches larger assets locally by first
transferring them over HTTP from the Confab server, then sending the file path and other Asset metadata to sclang via
OSC messages over UDP.

The three major components to the Confab system are therefore:

  * The Confab Server, called ```confab-server```, written in C++. Uses the Pistache library to keep a multi-threaded
    HTTP server available. Stores all Assets as records in a leveldb file database, serves as a centralized authority
    on all assets, and can stream Asset metadata and data chunks to clients on demand.
  * The Confab Client, called ```confab```, also written in C++. Uses the Pistache library to communicate with the
    server, maintains a local file-based cache of Assets, uses an LRU eviction strategy to delete older content from the
    cache. The client is a "proxy client" in that it communicates with the SuperCollider language process, ```sclang```,
    using the OSC protocol over UDP.
  * A suite of SuperCollider objects, namely ```SCLOrkConfab``` and ```SCLOrkAsset```, that represent SC-side language
    abstractions of asset metadata and provide access to these assets as needed. These classes are documented in the
    SuperCollider ```schelp``` format, along with their design document, and so are discussed here only to provide
    context for the C++ Confab system.

## Named Assets

The "name" field within an Asset record should indicate another way to look up an Asset by name. When an Asset is added
that has a name identifier field, that name is also added as a key in the database, with value as an 8-byte reference
back to the Asset. Lists can also have names, which are stored in a separate name lookup table. Names need to be at
least one character long.

## Lists

We add a FlatList data structure but the question is if that requires any special handling of list metadata or are the
same general things we are storing about Assets sufficient, and the difference is just that the payload of the list is
stored in another series of database entires. There is also the concept of "automatic lists," so for instance the list
of all Assets of a certain type, etc. And it is obvious it would be useful to have a concept like a "named list" which
is accessible in a manner similar to a named asset.

# Another Deprecation Line! Stuff Below Probably Still Useful Just Needs Rework

# Asset Streaming

Confab is likely to be running in a peformance environment and therefore needs to be very careful in its usage of memory
and disk resources. As a thought experiment, consider a large audio sample file (on the order of tens or possibly hundreds
of MB). This Asset is not available locally, so first Confab issues a request to the Confab server. This server
does have the file, so sends it along. Without asset streaming, the Confab instance is likely to allocate *at least one*
buffer of the size of the asset, if not more, between downloading the Asset from the server, writing it to the database,
and then saving it out to disk. Then when the SuperCollider synth process opens the audio file for sampling,
that means that there are now potentially two copies of the asset now loaded into RAM at different (but potentially overlapping)
times. For any asset over the asset size limit it is therefore proposed that a *streaming* mode be used for transmission,
storage, and retrieval of the asset. We break assets into individual chunks based on the default memory code page size
of 4K, which should hopefully keep memory pressure from Confab very low, while using memory pages efficiently, even when
handling very large assets.

Pistache can both transmit and receive in streaming mode, with flushes happening in increments of the asset size. These can
be sequentially added to the database as they are received.

xxHash can compute hashes in a streaming mode, meaning that on initial asset hash computation the asset file can be progressively
loaded into 4KB chunks at a time with the hash computed. It might be possible to double-buffer, meaning kick off an asynchronous
read into one buffer of the next 4KB of data while computing the hash of the current buffer. Some experimentation here of the
optimum streaming hash implementation is necessary.

LevelDB does not support streaming data into or out of individual keys but by appending a little-endian binary number to the data
keys one can load the asset sequntially using a LevelDB::Iterator across the keys. Some experimentation also required here to
determine the appropriate key size, there may be some overhead in allocation keeping paging at 4K kind of artificial.

OR - what if you build/find a block alloator? Something that does one allocation at the beginning and can associate keys with
blocks, so it knows what everyone is up to? Probably over-optimizing for now but could be an interesting thought long term.

ChunkSize is 4096 bytes.
InlineDataSize is 4096 bytes - some padding room for the rest of the metadata, say an even 4000 bytes or so. Maybe do a litle
math on the optional fields and decide from there. Maybe half that, conservatively, so 2048 bytes.

## So Asset Add plays out like this:

Incoming OSC message has either string (if shorter than InlineDataSize, and UTF-8 data) or path to file.

If it's a string compute the hash on the string, serialize the whole thing into an Asset record, save in DB, then return the key.
If it's a path start the streaming process for add:
  * Load the file in 4K chunks, considering multpile bufferings
  * Compute incremental hash on 4K chunks.
  * When hash is done create Asset Metadata record and record it.
Unfortunately for adds the streaming method requires two file traversals. Second traversal streams the individual chunks into
the database, and could also be double-buffered, or multi-buffered.

In order for other systems to access the asset it will need to be pushed upstream. There is a separate process for doing this,
but because upstream Internet connectivity may not be available the system should record the requests for upstreaming in the
database, and not delete them until they have been finalized. Each asset upstreaming request is a key/value pair with the
key being an upstream prepend byte 0x0a (chosen arbitrarily) followed by an 64-bit little-endian Unix epoch time value. The
value of the record is the complete key of the asset or asset data chunk that should be upstreamed. It also means that chunks
don't necessarily have to be streamed upstream, rather they can be sent as individual POST calls, allowing for some
paralellization as multiple threads read from the database and send the data upstream.

## Asset Read from OSC Side

Asset cache life is a tunable parameter per-asset. The LevelDB frontend typically has a LRU cache in front of it so it is
assumed that metadata reads on fast repeats should be pretty quick. It might be worth thinking about setting some flags
on the chunk reads so that a large sequential chunk read doesn't wipe out the cache with chunks. Each Asset record on an
edge can have a ttl that can force a refresh of the asset, for deprecation situations.

There should be a separate process that is always trying to maintain a valid upstream connection (multiple) to the upstream
server.

All incoming reads hit the LevelDB database first.
If found in the database:
  Check the TTL. If it's expired, queue up a refresh request, similar to upload request, but don't block return of this asset.
  If it's been deprecated then follow the chain of deprecations, announcing each one to the SuperCollider client as we go.
  If they fit in an OSC response and are of the appropriate type to do so then the response is sent immediately with the data.
  If they don't fit in the OSC response or are not of the appropriate type (not YAML or snippet) then a file return is needed, skip to that.
If not found in the database issue an upstream pull request. These also go in the database, with different behavior expected when
the pull is complete.

File Return: check for file exists already. If it does, just return it (e.g. send Asset message to SC via OSC). SC side will update
access time on file, which we use for SC-side file cache flushing.

## Upstream Communication

Polling process for establishing a connection to upstream server. Configurable timeout, default is longish, like 60 seconds.
Edge clients may want something much faster, as locally created assets can't be shared without that connection.

There are three interactios with upstream: asset refresh, asset pull, and asset add.

### Asset Refresh

The refresh requests are for a specific ID. The response will be a list of Assets. If the asset has not been deprecated the response
from the server will be the single asset metadata as requested. If the asset has been deprecated the response will be a series
of asset records connecting the deprecation from each to the next, until the current live asset has been found.

## Asset Pull

Doesn't just settle for metadata. Will peform the same request as a refresh, but if there's attached data on the associated asset
it will download it as well. There may be a std::future waiting on the results to notify the client, as well, so fulfill once
completed.

## Asset Add

On edge these are highest priority, as pulls from other clients won't work until this is complete. On non-leaf nodes these are
lowest priority.


# Stuff Below This is Deprecated and Needs a Rewrite

# Database Design {#Confab-Design-Document-Database-Design}

The [LevelDB](https://github.com/google/leveldb) library allows for serialization of resources directly to the database,
and supports both caching and compression. We rely on this library for the storage of both the metadata about a resource
as well as the actual resource binary data. The resource key identifier is the 128-bit hash as computed by the
[xxHash](https://github.com/Cyan4973/xxHash) XXH3 algorithm. This means that new assets can be created locally by
simply computing the hash of the asset before communicating the asset creation to the broader world via key.

## Database Configuration Key {#Confab-Design-Document-Database-Design-Database-Configuration-Key}

Confab stores one configuration entry under the key name ```confab-db-config```. The value of this key is a YAML
dictionary string with the following fields:

YAML Key             | Value Type | Description
---------------------|------------|------------
```version_major:``` | integer    | Major version number of confab that most recently opened this database. Confab names version numbers as *major*.*minor*.*sub*, for example 1.2.3 is major version 1, minor version 2, sub version 3.
```version_minor:``` | integer    | Minor version number of same.
```version_sub:```   | integer    | Sub version of the same.
```db_version:```    | integer    | Version of database schema. Currently 1.

## Metadata Configuration


## Asset Storage

Keys are stored in the LevelDB as ```asset-<Base64-encoded key value>```. Every asset stores a YAML metadata string,
and may include the asset binary directly in the YAML as a Base64-encoded string, or may provide the asset data
stored as a separate binary, under a key with UTF-8 string ```-data```` appended, and which will be provided as a
separate file when sent via HTTP.

The YAML metadata for an asset is a dictionary containing the following keys:

YAML Key           | Value Type | Required | Description
-------------------|------------|----------|------------
```size:```        | integer    | required | Size of asset in bytes.
```type:```        | string     | required | One of the enumerated type strings described in the Asset Type table.
```name:```        | string     | optional | Human-readable string name of this asset. Does not have to be unique to this asset.
```extension:```   | string     | optional | Filename extension of the binary blog, for example ```png```, ```yaml``` or ```wav```.
```creator:```     | string     | optional | An asset id of a person encoded as a Base64 string.
```deprecated:```  | string     | optional | An asset id that replaces this one.
```deprecates:```  | string     | optional | An asset id that this asset replaced.
```data_binary:``` | string     | optional | For small assets can optionally Base64 encode blob within the YAML directly.
```data_string:``` | string     | optional | At most one of ```data_binary``` or ```data_string``` keys can exist. Raw character data in UTF-8.
```ttl:```         | string     | optional | A date/time string for when this resource cache should be considered stale and refreshed.

Assets can currently be one of the following types:

Asset Type   | Type String   | Description
-------------|---------------|------------
Code Snippet | ```snippet``` | A snippet of SuperCollider code, which can be edited or executed directly.
Image        | ```image```   | An image file.
YAML         | ```yaml```    | YAML data in UTF-8 format.
Audio Sample | ```sound```   | A sound file.

# Network Topology

It is a system designed to allow any client to create new binary assets which are organized into a database and
synchronized in a pull basis using a tree of confab servers, at root of which is a confab program running in "canonical"
mode, meaning that it is the authoritative answer on all resources.

Each local mirror is designed with the principle that it may not always have a reliable connection to its upstream
mirror, and so will need to cache resource addition reports for streaming when a connection is re-established. There
may also be a need to build in better support for asset consistency checking due to loss of connectivity or client
mirrors having unreliable power or other software stability. The ensemble performance environment can be an
unpredictable one, so confab should design for robustness.

# Asset Wire Format


# Asset Request Protocol

When the SuperCollider client code issues a request for an asset from confab it will include the desired asset key. Confab
should check its local database for the availability of the asset, and if available it should extract the asset metadata from
the database, along with any additional supporting files, and inform the SuperCollider code of the availablility of the data
and the path to the files.

The asset in the database might also be marked as stale, in that when confab looks up the asset it
has a ```ttl``` field for a date that has expired. In that case confab should defer returning the asset to SuperCollider until
it verifies that the asset has not yet been deprecated.

The confab program will open a TCP port as specified in the configuration to handle incoming resource requests from
downstream mirrors via HTTP. It may also open up an OSC UDP port, possibly using SCLOrkWire, to process local client requests
for assets from SuperCollider. Lastly, there may be a configuration option to specify that this server is the
*canonical* or root server, meaning that there is no further upstream server available to process resource requests that
aren't available locally, and also that the canonical server never marks an asset in the cache as stale.

Asset fetch, and asset refresh.

For an asset Fetch confab mirror sends a GET HTTP request to upstream server with url ```/asset/fetch/{Base64 Asset ID}```. The
upstream confab mirror will repond with either an error code or 200 OK with mime type used to indicate return type. If
the return type is application/yaml the UTF-8 string returned is the entire asset entry, meaning there was no separate
data addenda.

# Asset Add Protocol

```/asset/add``` with a POST request.

# Resource Deprecation

# OSC Client Requests

- get metadata: comes back as YAML blob for parsing, also warms the local cache if asset isn't present
- get file: get a path to the local file extracted and decompressed from the db
- get string: pass the string directly over OSC
- get image: way to ask for an image that's been rescaled
- get meme: pre-process a meme, cache into file, send it
- copy code: push code assets directly to or from the paste buffer

