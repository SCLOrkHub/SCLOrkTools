SCLOrkChatServer {
	const <defaultListenPort = 7707;
	const clientPingTimeout = 10.0;

	var listenPort;

	// Map of individual wire objects to userIds.
	var <userIdMap;
	// Map of userIds to nicknames.
	var <nickNameMap;
	// Map of userIds to wire objects.
	var <wireMap;

	var userSerial;
	var timeoutsQueue;
	// Map of userIds to NetAddr objects, for clients
	// that have timed out.
	var timedOutAddrMap;

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

		SCLOrkWire.bind(
			port: listenPort,
			wireIssueId: {
				userIdMap.atFail(clientAddr, {
					userSerial = userSerial + 1;
					userSerial;
				});
			},
			wireOnConnected: { | wire, status |
			},
			wireOnMessageReceived: { | wire, msg |
				switch (msg[0],
					'/chatSignIn', {
						var nickName = msg[1];
						// Update user maps.
						userIdMap.put(wire, wire.targetId);
						nickNameMap.put(wire.targetId, nickName);
						wireMap.put(wire.targetId, clientAddr);
						timeoutsQueue.put(Main.elapsedTime + clientPingTimeout,
							wire.targetId);

						clientAddr.sendMsg('/chatSignInComplete', wire.targetId);

						// Send new client announcement to all connected clients.
						this.prSendAll(
							this.prChangeClient(\add, wire.targetId, nickName));
					},
					'/chatGetAllClients', {
						if (this.prScreenTimeout(wire.targetId), {
							var clientArray = [ '/chatSetAllClients' ] ++
							nickNameMap.getPairs;
							wire.sendMsg(*clientArray);
						});
					},
					'/chatPing', {
						if (this.prScreenTimeout(wire.targetId), {
							// Remove current timeout value from the priority queue.
							timeoutsQueue.removeValue(wire.targetId);
							// Re-add with new timeout.
							timeoutsQueue.put(Main.elapsedTime + clientPingTimeout,
								wire.targetId);
							// Respond with a pong and the timeout interval.
							clientAddr.sendMsg('/chatPong', clientPingTimeout);
						});
					},
					'/chatSendMessage', {
						var senderId, recipients, sendMessage, echoMessage;
						senderId = msg[1];
						if (this.prScreenTimeout(senderId), {
							recipients = msg[4..];
							sendMessage = ([ '/chatReceive' ] ++ msg[1..]);

							// If first recipient is 0 we send to all clients but sender.
							if (recipients[0] == 0, {
								wireMap.keysValuesDo({ | userId, clientWire |
									if (userId != senderId, {
										clientWire.sendMsg(*sendMessage);
									});
								});
							}, {
								recipients.do({ | userId, index |
									var clientWire = wireMap.at(userId);
									clientWire.sendMsg(*sendMessage);
								});
							});

							// Send echo message back to sender.
							echoMessage = ([ '/chatEcho' ] ++ msg[1..]);
							wire.sendMsg(*echoMessage);
						});
					},
					'/chatChangeNickname', {
						var nickName;
						if (this.prScreenTimeout(wire.targetId), {
							nickName = msg[1];
							nickNameMap.put(wire.targetId, nickName);
							this.prSendAll(
								this.prChangeClient(\rename, wire.targetId, nickName));
						});
					},
					'/chatSignOut', {
						var nickName;
						// We don't send a timedout response to timed out
						// clients trying to sign out.
						if (timedOutAddrMap.at(wire.targetId).isNil, {
							nickName = nickNameMap.at(wire.targetId);
							wireMap.removeAt(wire.targetId);
							userIdMap.removeAt(wire);
							nickNameMap.removeAt(wire.targetId);
							timeoutsQueue.removeValue(wire.targetId);
							this.prSendAll(
								this.prChangeClient(\remove, wire.targetId, nickName));
						});
					}
				);
			},
			onKnock: { | wire |
			}
		);

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

	prSendAll { | msgArray |
		userIdMap.keys.do({ | wire, index |
			wire.sendMsg(*msgArray);
		});
	}

	prChangeClient { | type, userId, nickName |
		^['/chatChangeClient',
			type,
			userId,
			nickName];
	}
}