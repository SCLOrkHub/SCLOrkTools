Confab Design Document
======================

[TOC]

The confab program is the C++-based support binary for the SCLOrkTools resource sharing system that powers things like
images, memes, emoji, and code sharing in SCLOrkChat.

This document serves as an overview of the design of the confab program.

Network Topology
-----------------

It is a system designed to allow any client to create new binary assets which are organized into a database and
synchronized in a pull basis using a tree of confab servers, at root of which is a confab program running in "canonical"
mode, meaning that it is the authoritative answer on all resources.

Each local mirror is designed with the principle that it may not always have a reliable connection to its upstream
mirror, and so will need to cache resource addition reports for streaming when a connection is re-established. There
may also be a need to build in better support for asset consistency checking due to loss of connectivity or client
mirrors having unreliable power or other software stability. The ensemble performance environment can be an
unpredictable one, so confab should design for robustness.

Asset Wire Format
-----------------

The LevelDB library allows for serialization of resources directly to the database, and supports both caching and
compression. We rely on this library for the storage of both the metadata about a resource as well as the actual
resource binary data. The resource key identifier is the 128-bit hash as computed by the
[xxHash](https://github.com/Cyan4973/xxHash) XXH3 algorithm. This means that new assets can be created locally by
simply computing the hash of the asset before communicating the asset creation to the broader world via key.

Keys are stored in the LevelDB as ```asset-<Base64-encoded key value>```. Every asset stores a YAML metadata string,
and may include the asset binary directly in the YAML as a Base64-encoded string, or may provide the asset data
stored as a separate binary, under a key with UTF-8 string ```-data```` appended, and which will be provided as a
separate file when sent via HTTP.

The YAML metadata for an asset is a dictionary containing the following keys:

YAML key           | Value Type | Required | Description
-------------------|------------|----------|------------
```size:```        | integer    | required | Size of asset in bytes.
```type:```        | string     | required | One of the enumerated type strings described in the Asset Type table.
```name:```        | string     | optional | Human-readable string name of this asset. Does not have to be unique to this asset.
```extension:```   | string     | optional | Filename extension of the binary blog, for example ```png```, ```yaml``` or ```wav```.
```creator:```     | string     | optional | An asset id of a person encoded as a Base64 string.
```deprecated:```  | string     | optional | An asset id that replaces this one.
```deprecates:```  | string     | optional | An asset id that this asset replaced.
```data_binary:``` | string     | optional | For small assets can optionally Base64 encode blob within the YAML directly.
```data_string:``` | string     | optional | At most one of ```data_binary``` or ```data_string``` keys can exist. Raw character data.
```ttl:```         | string     | optional | A date/time string for when this resource cache should be considered stale and refreshed.

Assets can currently be one of the following types:

Asset Type   | YAML string   | Description
-------------|---------------|------------
Code Snippet | ```snippet``` | A snippet of SuperCollider code, which can be edited or executed directly.
Image        | ```image```   | An image file.
YAML         | ```yaml```    | YAML data in UTF-8 format.
Audio Sample | ```sound```   | A sound file.

Asset Request Protocol
----------------------

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

For an asset Fetch confab mirror sends a GET HTTP request to upstream server with url /asset/fetch/{Base64 Asset ID}. The
upstream confab mirror will repond with either an error code or 200 OK with mime type used to indicate return type. If
the return type is application/yaml the UTF-8 string returned is the entire asset entry, meaning there was no separate
data addenda.

Asset Add Protocol
------------------


Resource Deprecation
--------------------


OSC Client Requests
-------------------

- get metadata: comes back as YAML blob for parsing, also warms the local cache if asset isn't present
- get file: get a path to the local file extracted and decompressed from the db
- get string: pass the string directly over OSC
- get image: way to ask for an image that's been rescaled
- get meme: pre-process a meme, cache into file, send it

