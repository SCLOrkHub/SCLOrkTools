(
"*** server startup %".format(Date.localtime).postln;

~chatServer = SCLOrkChatServer.new;
~timeServer = SCLOrkClockServer.new;

~chatBot = SCLOrkChatClient.new("127.0.0.1");
~chatBot.onMessageReceived = { | chatMessage |
	// We only respond to messages directed solely at us.
	if (chatMessage.recipientIds == [ ~chatBot.userId ], {
		// Message needs to be a shout, so that it is limited to directors.
		if (chatMessage.type == \shout, {
			// Lastly the specific contents of the message need to be '<shutdown>'
			if (chatMessage.contents == '<shutdown>', {
				var shutdownMessage = SCLOrkChatMessage.new(
					~chatBot.userId,
					[ 0 ],
					\plain,
					"Server received shutdown command, shutting down.");
				~chatBot.sendMessage(shutdownMessage);
				["sudo", "shutdown", "-h", "now"].unixCmd;

			});
		});
	});
};

~chatBot.connect("server-bot");
)
