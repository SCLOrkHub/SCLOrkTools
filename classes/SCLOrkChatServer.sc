SCLOrkChatServer {
	const <defaultListenPort = 7707;
	const clientPingTimeout = 10.0;

	var listenPort;

	// Map of individual NetAddr objects to userIds.
	var <userIdMap;
	// Map of userIds to nicknames.
	var <nickNameMap;
	// Map of userIds to NetAddr objects.
	var <netAddrMap;

	var userSerial;
	var timeoutsQueue;
	// Map of userIds to NetAddr objects, for clients
	// that have timed out.
	var timedOutAddrMap;

	var signInOscFunc;
	var getAllClientsOscFunc;
	var pingOscFunc;
	var sendMessageOscFunc;
	var changeNickNameOscFunc;
	var signOutOscFunc;

	var quitTasks;
	var timeoutClientsTask;

	*new { | listenPort = 7707 |
		^super.newCopyArgs(listenPort).init;
	}

	init {
		userIdMap = Dictionary.new;
		nickNameMap = Dictionary.new;
		netAddrMap = Dictionary.new;
		userSerial = 0;
		timeoutsQueue = PriorityQueue.new;
		timedOutAddrMap = Dictionary.new;

		signInOscFunc = OSCFunc.new({ | msg, time, addr |
			var nickName = msg[1];
			var clientPort = msg[2];
			var clientAddr = NetAddr.new(addr.ip, clientPort);
			var userId = userIdMap.atFail(clientAddr, {
				userSerial = userSerial + 1;
				userSerial;
			});

			// Update user maps.
			userIdMap.put(clientAddr, userId);
			nickNameMap.put(userId, nickName);
			netAddrMap.put(userId, clientAddr);
			timeoutsQueue.put(Main.elapsedTime + clientPingTimeout,
				userId);

			clientAddr.sendMsg('/chatSignInComplete', userId);

			// Send new client announcement to all connected clients.
			this.prSendAll(this.prChangeClient(\add, userId, nickName));

		},
		path: '/chatSignIn',
		recvPort: listenPort
		).permanent_(true);

		getAllClientsOscFunc = OSCFunc.new({ | msg, time, addr |
			var userId = msg[1];
			if (this.prScreenTimeout(userId), {
				var clientAddr = netAddrMap.at(userId);
				var clientArray = [ '/chatSetAllClients' ] ++
				nickNameMap.getPairs;
				clientAddr.sendRaw(clientArray.asRawOSC);
			});
		},
		path: '/chatGetAllClients',
		recvPort: listenPort
		).permanent_(true);

		pingOscFunc = OSCFunc.new({ | msg, time, addr |
			var userId = msg[1];
			if (this.prScreenTimeout(userId), {
				var clientAddr = netAddrMap.at(userId);
				// Remove current timeout value from the priority queue.
				timeoutsQueue.removeValue(userId);
				// Re-add with new timeout.
				timeoutsQueue.put(Main.elapsedTime + clientPingTimeout,
					userId);
				// Respond with a pong and the timeout interval.
				clientAddr.sendMsg('/chatPong', userId, clientPingTimeout);
			});
		},
		path: '/chatPing',
		recvPort: listenPort
		).permanent_(true);

		sendMessageOscFunc = OSCFunc.new({ | msg, time, addr |
			var senderId, recipients, sendMessage, senderAddr, echoMessage;
			senderId = msg[1];
			if (this.prScreenTimeout(senderId), {
				recipients = msg[4..];
				sendMessage = ([ '/chatReceive' ] ++ msg[1..]).asRawOSC;

				// If first recipient is 0 we send to all clients but the sender.
				if (recipients[0] == 0, {
					netAddrMap.keysValuesDo({ | userId, clientAddr |
						if (userId != senderId, {
							clientAddr.sendRaw(sendMessage);
						});
					});
				}, {
					recipients.do({ | userId, index |
						var clientAddr = netAddrMap.at(userId);
						clientAddr.sendRaw(sendMessage);
					});
				});

				// Send echo message back to sender.
				senderAddr = netAddrMap.at(senderId);
				echoMessage = ([ '/chatEcho' ] ++ msg[1..]).asRawOSC;
				senderAddr.sendRaw(echoMessage);
			});
		},
		path: '/chatSendMessage',
		recvPort: listenPort
		).permanent_(true);

		changeNickNameOscFunc = OSCFunc.new({ | msg, time, addr |
			var userId, nickName;
			userId = msg[1];
			if (this.prScreenTimeout(userId), {
				nickName = msg[2];
				nickNameMap.put(userId, nickName);
				this.prSendAll(this.prChangeClient(\rename, userId, nickName));
			});
		},
		path: '/chatChangeNickName',
		recvPort: listenPort
		).permanent_(true);

		signOutOscFunc = OSCFunc.new({ | msg, time, addr |
			var userId, clientAddr, nickName;
			// We don't send a timedout response to timed out
			// clients trying to sign out.
			if (timedOutAddrMap.at(userId).isNil, {
				userId = msg[1];
				nickName = nickNameMap.at(userId);
				clientAddr = netAddrMap.at(userId);
				nickName = nickNameMap.at(userId);
				netAddrMap.removeAt(userId);
				userIdMap.removeAt(clientAddr);
				nickNameMap.removeAt(userId);
				timeoutsQueue.removeValue(userId);
				this.prSendAll(this.prChangeClient(\remove, userId, nickName));
			});
		},
		path: '/chatSignOut',
		recvPort: listenPort
		).permanent_(true);

		quitTasks = false;

		timeoutClientsTask = SkipJack.new({
			while ({ timeoutsQueue.notEmpty and: {
				Main.elapsedTime > timeoutsQueue.topPriority }}, {
				var userId = timeoutsQueue.pop;
				var nickName = nickNameMap.at(userId);
				var clientAddr = netAddrMap.at(userId);
				// Add this client to the timed out map.
				timedOutAddrMap.put(userId, clientAddr);

				// Remove from other maps.
				netAddrMap.removeAt(userId);
				userIdMap.removeAt(clientAddr);
				nickNameMap.removeAt(userId);

				clientAddr.sendMsg('/chatTimedOut');
				this.prSendAll(this.prChangeClient(\timeout, userId, nickName));
			});
		},
		dt: 1.0,
		stopTest: { quitTasks },
		name: "ChatServerTimeoutClientsCheck",
		clock: SystemClock,
		autostart: true
		);

		^this;
	}

	free {
		signInOscFunc.free;
		getAllClientsOscFunc.free;
		pingOscFunc.free;
		sendMessageOscFunc.free;
		changeNickNameOscFunc.free;
		signOutOscFunc.free;
		quitTasks = true;
		timeoutClientsTask.stop;
	}

	// Returns true if the user HASN'T timed out.
	prScreenTimeout { | userId |
		if (timedOutAddrMap.at(userId).notNil, {
			var clientAddr = timedOutAddrMap.at(userId);
			clientAddr.sendMsg('/chatTimedOut');
			^false;
		}, {
			^true;
		});
	}

	prRemoveUserId { | userId |
		var clientAddr, nickName;
	}

	prSendAll { | msgArray |
		userIdMap.keys.do({ | clientAddr, index |
			clientAddr.sendRaw(msgArray);
		});
	}

	prChangeClient { | type, userId, nickName |
		^['/chatChangeClient',
			type,
			userId,
			nickName].asRawOSC;
	}
}