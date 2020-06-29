SCLOrkChatClient {
	var serverAddress;
	var serverPort;
	var netAddr;

	var signInCompleteFunc;
	var setAllClientsFunc;
	var changeClientFunc;
	var chatReceiveFunc;

	var pollTask;

	var <name;  // self-assigned name, can be changed.
	var <userId;

	var messageSerial;

	var <nameMap;  // map of userIds to values.

	// Callbacks, functions to be called when status changes.
	var <>onConnected;  // called on connection status change with bool argument
	var <>onMessageReceived;  // called with chatMessage object on receipt
	var <>onUserChanged;  // called with user changes, type, userid, nickname.

	*new { |serverAddress = "cmn17.stanford.edu", serverPort = 61010|
		^super.newCopyArgs(serverAddress, serverPort).init;
	}

	init {
		netAddr = NetAddr.new(serverAddress, serverPort);

		signInCompleteFunc = OSCFunc.new({ |msg|
			userId = msg[1];
			netAddr.sendMsg('/chatGetAllClients');
		},
		path: '/chatSignInComplete',
		srcID: netAddr);

		setAllClientsFunc = OSCFunc.new({ |msg|
			nameMap.clear;
			nameMap.putPairs(msg[1..]);
			pollTask = SkipJack.new({
				if (netAddr.isConnected, {
					netAddr.sendMsg('/chatGetMessages', userId, messageSerial);
				});
			},
			dt: 0.5);
			// Since wire is connected and we have a complete user dictionary,
			// we consider the chat client now connected.
			onConnected.(true);
		},
		path: '/chatSetAllClients',
		srcID: netAddr);

		changeClientFunc = OSCFunc.new({ |msg|
			var serial, changeType, id, userName, oldName, changeMade;
			serial = msg[1];
			if (serial > messageSerial, {
				messageSerial = serial;
				changeType = msg[2];
				id = msg[3];
				userName = msg[4];
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
			});
		},
		path: '/chatChangeClient',
		srcID: netAddr);

		chatReceiveFunc = OSCFunc.new({ |msg|
			var serial = msg[1];
			if (serial > messageSerial, {
				var chatMessage = SCLOrkChatMessage.new(
					msg[2],
					msg[5..],
					msg[3],
					msg[4],
					nameMap.at(msg[2]),
					msg[2] == userId);
				messageSerial = serial;
				// Populate list of recipient names if it is not a broadcast.
				if (chatMessage.recipientIds[0] != 0, {
					chatMessage.recipientNames = nameMap.atAll(
						chatMessage.recipientIds);
				});
				onMessageReceived.(chatMessage);
			});
		},
		path: '/chatReceive',
		srcID: netAddr);

		name = "default-nickname";
		messageSerial = 0;
		nameMap = Dictionary.new;
		onConnected = {};
		onMessageReceived = {};
		onUserChanged = {};
	}

	connect { | clientName |
		name = clientName;
		netAddr.tryConnectTCP(
			onComplete: { netAddr.sendMsg('/chatSignIn', name); },
			onFailure: { onConnected.(false) });
	}

	disconnect {
		pollTask.stop;
		netAddr.sendMsg('/chatSignOut', userId);
		netAddr.disconnect;
	}

	free {
		if (netAddr.isConnected, {
			this.disconnect;
		});
		signInCompleteFunc.free;
		setAllClientsFunc.free;
		changeClientFunc.free;
		chatReceiveFunc.free;
	}

	name_ { | newName |
		name = newName;
		netAddr.sendMsg('/chatChangeName', userId, name);
	}

	isConnected {
		^netAddr.isConnected;
	}

	sendMessage { | chatMessage |
		var message = ['/chatSendMessage',
			userId,
			chatMessage.type,
			chatMessage.contents] ++ chatMessage.recipientIds;

		netAddr.sendMsg(*message);
	}

/*
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
*/
}
