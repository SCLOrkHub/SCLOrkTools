SCLOrkChatClient {
	var serverAddress;
	var serverPort;
	var listenPort;
	var wire;
	var <userId;

	var <name;  // self-assigned name, can be changed.

	var <nameMap;  // map of userIds to values.

	// Callbacks, functions to be called when status changes.
	var <>onConnected;  // called on connection status change with bool argument
	var <>onMessageReceived;  // called with chatMessage object on receipt
	var <>onUserChanged;  // called with user changes, type, userid, nickname.

	*new { | serverAddress = "sclork-s01.local", serverPort = 8000, listenPort = 7705 |
		^super.newCopyArgs(serverAddress, serverPort, listenPort).init;
	}

	init {
		wire = SCLOrkWire.new(listenPort);
		wire.onConnected = { | wire, state |
			switch (state,
				\connected, {
					wire.sendMsg('/chatSignIn', name);
				},
				\failureTimeout, {
					onConnected.(false);
				},
				\disconnected, {
					onConnected.(false);
				}
			);
		};
		wire.onMessageReceived = { | wire, msg |
			switch (msg[0],
				'/chatSignInComplete', {
					// Our userId is the sever-assigned wire
					userId = wire.selfId;
					// Send a request for all currently logged-in clients.
					wire.sendMsg('/chatGetAllClients');
				},
				'/chatSetAllClients', {
					nameMap.clear;
					nameMap.putPairs(msg[1..]);
					// Since wire is connected and we have a complete user
					// dictionary, we consider the chat client now connected.
					onConnected.(true);
				},
				'/chatChangeClient', {
					var changeType, id, userName, oldName, changeMade;
					changeType = msg[1];
					id = msg[2];
					userName = msg[3];
					changeMade = true;
					switch (changeType,
						\add, {
							nameMap.put(id, userName);
							// Suppress announcement of our own connection.
							if (id == userId, {
								changeMade = false;
							});
						},
						\remove, {
							// It is possible with timed out clients we may receive
							// some few messages from them, so only process clients
							// when they are still in the dictionary.
							if (nameMap.at(id).notNil, {
								nameMap.removeAt(id);
							}, {
								changeMade = false;
							});
						},
						\rename, {
							oldName = nameMap.at(id);
							nameMap.put(id, userName);
						},
						\timeout, {
							nameMap.removeAt(id);
						},
						{
							"unknown change ordered to client user dict.".postln;
							changeMade = false;
						}
					);
					if (changeMade, {
						onUserChanged.(changeType, id, userName, oldName);
					});
				},
				'/chatReceive', {
					var chatMessage = SCLOrkChatMessage.new(
						msg[1],
						msg[4..],
						msg[2],
						msg[3],
						nameMap.at(msg[1]),
						false);
					// Populate list of recipient names if it is not a broadcast.
					if (chatMessage.recipientIds[0] != 0, {
						chatMessage.recipientNames = nameMap.atAll(
							chatMessage.recipientIds);
					});
					onMessageReceived.(chatMessage);
				},
				'/chatEcho', {
					var chatMessage = SCLOrkChatMessage.new(
						msg[1],
						msg[4..],
						msg[2],
						msg[3],
						nameMap.at(msg[1]),
						true);
					// Populate list of recipient names if it is not a broadcast.
					if (chatMessage.recipientIds[0] != 0, {
						chatMessage.recipientNames = nameMap.atAll(
							chatMessage.recipientIds);
					});
					onMessageReceived.(chatMessage);
				}
			);
		};
		name = "default-nickname";
		nameMap = Dictionary.new;
		onConnected = {};
		onMessageReceived = {};
		onUserChanged = {};
	}

	connect { | clientName |
		name = clientName;
		wire.knock(serverAddress, serverPort);
	}

	disconnect {
		wire.sendMsg('/chatSignOut');
	}

	free {
		if (wire.connectionState === \connected, {
			this.disconnect;
		});
	}

	name_ { | newName |
		name = newName;
		wire.sendMsg('/chatChangeName', name);
	}

	isConnected {
		^(wire.connectionState === \connected);
	}

	sendMessage { | chatMessage |
		var message = ['/chatSendMessage',
			chatMessage.type,
			chatMessage.contents] ++ chatMessage.recipientIds;

		wire.sendMsg(*message);
	}

	prForceTimeout {
		wire.prDropLine;
		this.sendMessage(SCLOrkChatMessage.new(
			userId,
			[ 0 ],
			\system,
			"% forcing a timeout.".format(name)));
	}

	prUnwedge {
		wire.prBindOSC;
	}
}
