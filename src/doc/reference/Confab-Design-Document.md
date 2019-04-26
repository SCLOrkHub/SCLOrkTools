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
mirrors having unreliable power or other software stability. The performance environment can be an unpredictable one,
so confab should design for robustness.

Resource Wire Format
--------------------
The LevelDB library allows for serialization of resources directly to the database, and supports both caching and
compression. We rely on this library for the storage of both the metadata about a resource as well as the actual
resource binary data. The resource key identifier is the 128-bit hash as computed by the
[xxHash](https://github.com/Cyan4973/xxHash) XXH3 algorithm. This means that new assets can be created locally by
simply computing the hash of the asset before communicating the asset creation to the broader world via key.

The resource is a binary blob consisting of a YAML metadata section, followed by a ```\0``` null terminator, then
followed by a binary blob of size specified in the YAML portion, if appropriate.

Assets can currently be one of the following types:

Asset Type   | YAML string   | Description
-------------|---------------|------------
Code Snippet | ```snippet``` | A snippet of SuperCollider code, which can be edited or executed directly.
Image        | ```image```   | An image file.
Meme         | ```meme```    | Additional metadata consisting of an image asset reference and string to construct a meme.
Audio Sample | ```sample```  | A sound file.
Person       | ```person```  | A collection of metadata around an identity.

The YAML metadata is a dictionary containing the following keys:

YAML key         | Value type | Required | Description
-----------------|------------|----------|------------
```name:```      | string     | optional | Human-readable string name of this asset. Does not have to be unique to this asset.
```size:```      | integer    | required | Size of blob concatenated to YAML string. Can be zero.
```extension:``` | string     | optional | Filename extension of the binary blog, for example ```png``` or ```wav```.
```creator:```   | string     | optional | An asset id of a person encoded as a hexadecimal 128-bit string.

Resource Request Protocol
-------------------------

The confab program will open a TCP port as specified in the configuration to handle incoming resource requests from
downstream mirrors. It may also open up an OSC UDP port, possibly using SCLOrkWire, to process local client requests
for assets from SuperCollider. Lastly, there may be a configuration option to specify that this server is the
*canonical* or root server, meaning that there is no further upstream server available to process resource requests that
aren't available locally.

Resource Add Protocol
---------------------


Resource Deprecation
--------------------


OSC Client Requests
-------------------

- get metadata: comes back as YAML blob for parsing, also warms the local cache if asset isn't present
- get file: get a path to the local file extracted and decompressed from the db
- get string: pass the string directly over OSC
- get image: way to ask for an image that's been rescaled
- get meme: pre-process a meme, cache into file, send it

