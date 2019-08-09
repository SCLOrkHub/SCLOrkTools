SCLOrkChatServer {
	// TODO: class may be deprecated, not needed anymore.

	const <defaultBindPort = 8000;
	var bindPort;

	// Map of userIds to nicknames.
	var <nameMap;

	// Map of userIds to wire objects.
	var <wireMap;

	var wireSerial;

	*new { | bindPort = 8000 |
		^super.newCopyArgs(bindPort).init;
	}

	init {
		nameMap = Dictionary.new;
		wireMap = Dictionary.new;
		wireSerial = 1;

		SCLOrkWire.bind(
			port: bindPort,
			wireIssueId: {
				var serial = wireSerial;
				wireSerial = wireSerial + 1;
				serial;
			},
			wireOnConnected: { | wire, status |
				switch (status,
					\connected, {
						wireMap.put(wire.id, wire);
					},
					\failureTimeout, {
						// Remove client from our maps, inform other clients of
						// the change.
						var name = nameMap.at(wire.id);
						wireMap.removeAt(wire.id);
						nameMap.removeAt(wire.id);
						this.prSendAll(
							this.prChangeClient(\timeout, wire.id, name));
					},
					\disconnected, {
						// Should have already been removed from nameMap but
						// just in case remove here.
						if (nameMap.at(wire.id).notNil, {
							var name = nameMap.at(wire.id);
							nameMap.removeAt(wire.id);
							this.prSendAll(
								this.prChangeClient(\remove, wire.id, name), wire);
						});
						wireMap.removeAt(wire.id);
					}
				);
			},
			wireOnMessageReceived: { | wire, msg |
				switch (msg[0],
					'/chatSignIn', {
						var name = msg[1];
						nameMap.put(wire.id, name);
						wire.sendMsg('/chatSignInComplete');

						// Send new client announcement to all connected clients.
						this.prSendAll(
							this.prChangeClient(\add, wire.id, name));
					},
					'/chatGetAllClients', {
						var clientArray;

						// Reconcile everyone in the nameMap with the wireMap.
						if (nameMap.size != wireMap.size, {
							var ghosts = nameMap.keys.difference(wireMap.keys);
							ghosts.do({ | item, index |
								var name = nameMap.at(item) ++ " (ghost)";
								nameMap.removeAt(item);
								this.prSendAll(
									this.prChangeClient(\timeout, item, name), wire);
							});
						});

						clientArray = [ '/chatSetAllClients' ] ++ nameMap.getPairs;
						wire.sendMsg(*clientArray);
					},
					'/chatSendMessage', {
						var recipients, sendMessage, echoMessage;
						recipients = msg[3..];
						sendMessage = ([ '/chatReceive', wire.id ] ++ msg[1..]);

						// If first recipient is 0 we send to all clients but sender.
						if (recipients[0] == 0, {
							this.prSendAll(sendMessage, wire);
						}, {
							recipients.do({ | id, index |
								var clientWire = wireMap.at(id);
								clientWire.sendMsg(*sendMessage);
							});
						});

						// Send echo message back to sender.
						echoMessage = ([ '/chatEcho', wire.id ] ++ msg[1..]);
						wire.sendMsg(*echoMessage);
					},
					'/chatChangeName', {
						var newName = msg[1];
						nameMap.put(wire.id, newName);
						this.prSendAll(
							this.prChangeClient(\rename, wire.id, newName));
					},
					'/chatSignOut', {
						var name = nameMap.at(wire.id);
						nameMap.removeAt(wire.id);
						// Start normal connection termination process.
						wire.disconnect;
						// Inform all other clients of disconnect.
						this.prSendAll(
							this.prChangeClient(\remove, wire.id, name), wire);
					}
				);
			},
			onKnock: { | wire |
				// We wait for clients to sign in with \connected,
				// so no need to add additional tracking here.
			}
		);

		this;
	}

	free {
		SCLOrkWire.unbind(bindPort);
	}

	prSendAll { | msgArray, skipWire = nil |
		wireMap.values.do({ | wire, index |
			if (skipWire.isNil or: { skipWire.id != wire.id }, {
				wire.sendMsg(*msgArray);
			});
		});
	}

	prChangeClient { | type, userId, nickName |
		^['/chatChangeClient',
			type,
			userId,
			nickName];
	}
}