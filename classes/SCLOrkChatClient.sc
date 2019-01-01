SCLOrkChatClient {
	const defaultListenPort = 7705;

	var serverNetAddr;
	var listenPort;
	var <nickName;  // self-assigned nickname, can be changed.

	var <userDictionary;  // map of userIds to values.
	var <isConnected;  // state of connection to server.
	var <userId;  // server-assigned userId.

	var signInCompleteOscFunc;
	var setAllClientsOscFunc;
	var pongOscFunc;
	var changeClientOscFunc;
	var receiveOscFunc;
	var echoOscFunc;
	var timeoutOscFunc;

	var quitTasks;
	var pingTask;

	// Callbacks, functions to be called when status changes.
	var <>onConnected;  // called on connection status change with bool argument
	var <>onMessageReceived;  // called with chatMessage object on receipt
	var <>onUserChanged;  // called with user changes, type, userid, nickname.

	*new { | serverNetAddr, listenPort = 7705 |
		^super.newCopyArgs(serverNetAddr, listenPort).init;
	}

	init {
		nickName = "default-nickname";
		userDictionary = Dictionary.new;
		isConnected = false;
		onConnected = {};
		onMessageReceived = {};
		onUserChanged = {};

		signInCompleteOscFunc = OSCFunc.new({ | msg, time, addr |
			// TODO: Can double-check isConnected to be false here, throw
			// error if receiving duplicate signInComplete message.
			isConnected = true;
			userId = msg[1];
			// Send a request for a list of all connected clients,
			// as well as our first ping message.
			serverNetAddr.sendMsg('/chatGetAllClients', userId);
			// Send first ping, to get server timeout.
			serverNetAddr.sendMsg('/chatPing', userId);
		},
		path: '/chatSignInComplete',
		recvPort: listenPort
		).permanent_(true);

		setAllClientsOscFunc = OSCFunc.new({ | msg, time, addr |
			var num, id;
			userDictionary.clear;
			userDictionary.putPairs(msg[1..]);

			// We consider the client to be connected when it has both
			// a valid userId and userDictionary.
			isConnected = true;
			onConnected.(true);
		},
		path: '/chatSetAllClients',
		recvPort: listenPort
		).permanent_(true);

		pongOscFunc = OSCFunc.new({ | msg, time, addr |
			var serverTimeout = msg[2];

			if (pingTask.isNil, {
				// Set up the ping task for an interval designed to
				// safely survive one dropped ping.
				quitTasks = false;
				pingTask = SkipJack.new({
					serverNetAddr.sendMsg('/chatPing', userId);
				},
				dt: (serverTimeout / 2.0) - 1.0,
				stopTest: { quitTasks },
				name: "ChatPingTask",
				clock: SystemClock,
				autostart: true
				);
			});
		},
		path: '/chatPong',
		recvPort: listenPort
		).permanent_(true);

		changeClientOscFunc = OSCFunc.new({ | msg, time, addr |
			var changeType, id, nickname, oldname, changeMade;
			changeType = msg[1];
			id = msg[2];
			nickname = msg[3];
			changeMade = true;
			switch (changeType,
				\add, {
					userDictionary.put(id, nickname);
					// Suppress announcement of our own connection.
					if (id == userId, {
						changeMade = false;
					});
				},
				\remove, {
					// It is possible with timed out clients we may receive
					// some few messages from them, so only process clients
					// when they are still in the dictionary.
					if (userDictionary.at(id).notNil, {
						userDictionary.removeAt(id);
					}, {
						changeMade = false;
					});
				},
				\rename, {
					oldname = userDictionary.at(id);
					userDictionary.put(id, nickname);
				},
				\timeout, {
					userDictionary.removeAt(id);
				},
				{ "unknown change ordered to client user dict.".postln; });

			if (changeMade, {
				onUserChanged.(changeType, id, nickname, oldname);
			});
		},
		path: '/chatChangeClient',
		recvPort: listenPort
		).permanent_(true);

		receiveOscFunc = OSCFunc.new({ | msg, time, addr |
			var chatMessage = SCLOrkChatMessage.new(
				msg[1],
				msg[4..],
				msg[2],
				msg[3],
				userDictionary.at(msg[1]),
				false);
			if (chatMessage.recipientIds[0] != 0, {
				chatMessage.recipientNames = userDictionary.atAll(
					chatMessage.recipientIds);
			});
			onMessageReceived.(chatMessage);
		},
		path: '/chatReceive',
		recvPort: listenPort
		).permanent_(true);

		echoOscFunc = OSCFunc.new({ | msg, time, addr |
			var chatMessage = SCLOrkChatMessage.new(
				msg[1],
				msg[4..],
				msg[2],
				msg[3],
				userDictionary.at(msg[1]),
				true);
			if (chatMessage.recipientIds[0] != 0, {
				chatMessage.recipientNames = userDictionary.atAll(
					chatMessage.recipientIds);
			});
			onMessageReceived.(chatMessage);
		},
		path: '/chatEcho',
		recvPort: listenPort
		).permanent_(true);

		timeoutOscFunc = OSCFunc.new({ | msg, time, addr |
			if (isConnected, { this.disconnect; });
		},
		path: '/chatTimedOut',
		recvPort: listenPort
		).permanent_(true);
	}

	connect { | name |
		nickName = name;
		serverNetAddr.sendMsg('/chatSignIn', nickName, listenPort);
	}

	disconnect {
		pingTask.stop;
		pingTask = nil;
		serverNetAddr.sendMsg('/chatSignOut', userId, listenPort);
		userId = nil;
		isConnected = false;
		onConnected.(false);
	}

	free {
		if (isConnected, { this.disconnect; });

		signInCompleteOscFunc.free;
		setAllClientsOscFunc.free;
		pongOscFunc.free;
		changeClientOscFunc.free;
		receiveOscFunc.free;
		echoOscFunc.free;
	}

	nickName_ { | newNick |
		nickName = newNick;
		serverNetAddr.sendMsg('/chatChangeNickName', userId, newNick);
	}

	sendMessage { | chatMessage |
		var message = ['/chatSendMessage',
			userId,
			chatMessage.type,
			chatMessage.contents] ++ chatMessage.recipientIds;

		serverNetAddr.sendRaw(message.asRawOSC);
	}

	// For testing only, halts the ping task.
	prForceTimeout {
		pingTask.stop;
	}
}
