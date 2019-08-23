SCLOrkChatClient {
	const clientUpdatePeriodSeconds = 30.0;

	var <userId;  // signed-in userId
	var <userMap;  // userId -> immutable Asset.
	var <nameMap;  // Current nickname, subject to change.
	var <currentUsers;

	var quitTasks;
	var clientUpdateTask;

	var <>onConnected;  // called on connection status change with bool argument
	var <>onMessageReceived;  // called with chatMessage object on receipt
	var <>onUserChanged;  // called with user changes, type, userid, nickname.

	*new {
		^super.new.init;
	}

	init {
		quitTasks = false;
		userMap = IdentityDictionary.new;
		nameMap = IdentityDictionary.new;
		currentUsers = IdentitySet.new;

		this.getUsers();
	}

	getUsers {
		Routine.new({
			var c = Condition.new;
			var userListId, userIds;

			c.test = false;
			SCLOrkConfab.findListByName('Users', { |name, key|
				userListId = key;
				c.test = true;
				c.signal;
			});
			c.wait;

			// Now we get all the users on the list.
			c.test = false;
			SCLOrkConfab.getListNext(userListId, '0', { |id, tokens|
				userIds = tokens.asString
				.split($\n)
				.collect({|p| p.split($ )})
				.reject({|p| p.size != 2 })
				.collect({|p| p[1].asSymbol })
				.select({|p| SCLOrkConfab.idValid(p) });
				c.test = true;
				c.signal;
			});
			c.wait;

			// Look up everybody who is not in the current userMap.
			userIds.do({ |id, index|
				if (userMap.at(id).isNil, {
					c.test = false;
					SCLOrkConfab.findAssetById(id, { |foundId, asset|
						userMap.put(id, asset.inlineData);
						c.test = true;
						c.signal;
					});
					c.wait;
				});
			});
		}).play;
	}

	connect { |withUserId|
		userId = withUserId;
		SCLOrkConfab.setUser(userId);
	}

	// ok do we even care about online status? Like everything is immutable. If necessary/interested a
	// person can monitor state of current network environment. But really the chat is a lot more like
	// low-latency email these days, meaning that "who's online" is a much less important concept for
	// typical users, perhaps only relevant to the director/sysadmin types. So fidelity can be fairly
	// low, perhaps even emulated with some message last sent kind of thing. Or by a very occasional call
	// on the SCLOrkChat directly to the status thing. The list of users should just grow monotonically.
	prStartClientUpdate {
		clientUpdateTask = SkipJack.new({
			var c = Condition.new;
			Routine.new({
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
					if (user !== '0000000000000000', {
						latestUsers.add(user);
					});
				});

				// New users are the ones present in the new set but not in the current one.
				newUsers = latestUsers - currentUsers;
				droppedUsers = currentUsers - latestUsers;
				currentUsers = latestUsers;

				droppedUsers.do({ |id, index|
					var name = nameMap.at(id);
					onUserChanged.value(\remove, id, name, name);
				});

				newUsers.do({ |id, index|
					if (userMap.at(id).isNil, {
						c.test = false;
						SCLOrkConfab.findAssetById(id, { |id, asset|
							userMap.put(id, asset.inlineData);
							c.test = true;
							c.signal;
						});
						c.wait;
					});
				});
			});
		},
		dt: clientUpdatePeriodSeconds,
		stopTest: { quitTasks },
		name: "ChatClientUpdate",
		clock: SystemClock,
		autostart: true
		);
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
