SCLOrkChatClient {
	const clientUpdatePeriodSeconds = 30.0;
	const messageUpdatePeriodSeconds = 1.0;

	var <userId;  // signed-in userId
	var users;  // reference to SCLOrkUsers singleton instance.
	var <currentUsers;  // IdentitySet of userIds, updated slowly (clientUpdatePeriodSeconds)

	var <lastToken;  // the list token of the last message loaded from the server.
	var messageListId;

	var quitTasks;
	var clientUpdateTask;
	var messageUpdateTask;

	var <>onConnected;  // called on connection status change with bool argument
	var <>onMessageReceived;  // called with chatMessage object on receipt
	var <>onUserChanged;  // called on user changes, type (\offline or \online), userid.

	*new {
		^super.new.init;
	}

	init {
		quitTasks = false;
		currentUsers = IdentitySet.new;
		lastToken = SCLOrkConfab.startToken;
		users = SCLOrkUsers.new;
		users.update();

		this.prStartClientUpdate();
		this.prStartMessageUpdate();
	}

	connect { |withUserId|
		userId = withUserId;
		SCLOrkConfab.setUser(userId);
	}

	sendMessage { |message|
		SCLOrkConfab.addAssetString('yaml', "", [ messageListId ], message.toYAML, { |id|
			// right now these callbacks just go into a void.
		});
	}

	prStartClientUpdate {
		clientUpdateTask = SkipJack.new({
			Routine.new({
				var c = Condition.new;
				var states;
				var newUsers, droppedUsers;
				var latestUsers = IdentitySet.new;

				c.test = false;

				SCLOrkConfab.getStates({ |newStates|
					states = newStates;
					c.test = true;
					c.signal;
				});
				c.wait;

				// Extract non-zero userIds from the status values.
				states.keysValuesDo({ |address, state|
					var user = state.asString.split($|)[0].asSymbol;
					if (SCLOrkConfab.idValid(user), {
						latestUsers.add(user);
					});
				});

				// New users are the ones present in the new set but not in the current one.
				newUsers = latestUsers - currentUsers;
				droppedUsers = currentUsers - latestUsers;
				currentUsers = latestUsers;

				droppedUsers.do({ |id, index|
					onUserChanged.value(\offline, id);
				});

				newUsers.do({ |id, index|
					if (users.userMap.at(id).isNil, {
						c.test = false;
						users.lookupUser(id, {
							c.test = true;
							c.signal;
						});
						c.wait;
					});
					onUserChanged.value(\online, id);
				});
			}).play;
		},
		dt: clientUpdatePeriodSeconds,
		stopTest: { quitTasks },
		name: "ChatClientUpdate",
		clock: SystemClock,
		autostart: true
		);
	}

	prStartMessageUpdate {
		// Note autostart is false onn this task, to avoid a race condition around trying to look
		// up new messages for the messageListId when it is undefined.
		messageUpdateTask = SkipJack.new({
			Routine.new({
				var c = Condition.new;
				var messages;

				c.test = false;
				SCLOrkConfab.getListNext(messageListId, lastToken, { |id, tokens|
					messages = tokens.reject({|t| t === SCLOrkConfab.endToken });
					c.test = true;
					c.signal;
				});
				c.wait;

				messages.pairsDo({ |token, messageId|
					var message;
					// Request full message Asset and provide as update.
					c.test = false;
					SCLOrkConfab.findAssetById(messageId, { |id, asset|
						message = SCLOrkChatMessage.newFromDictionary(asset.inlineData);
						c.test = true;
						c.signal;
					});
					c.wait;
					lastToken = token;
					onMessageReceived(message);
				});
			}).play;
		},
		dt: messageUpdatePeriodSeconds,
		stopTest: { quitTasks },
		name: "ChatMessageUpdate",
		clock: SystemClock,
		autostart: false
		);

		Routine.new({
			var c = Condition.new;
			c.test = false;
			SCLOrkConfab.findListByName('Chat Messages', { |name, key|
				messageListId = key;
				c.test = true;
				c.signal;
			});
			c.wait;

			// Now that we have messageListId defined we can start the update loop.
			messageUpdateTask.start;
		}).play;
	}
}

SCLOrkChatClientOld {
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
