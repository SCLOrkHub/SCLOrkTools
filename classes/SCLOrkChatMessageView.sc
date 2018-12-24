SCLOrkChatMessageView : View {
	var labelStaticText;
	var actionButton;
	var contentsTextView;

	*new { | chatMessage, messageIndex |
		^super.new.init(chatMessage, messageIndex);
	}

	init { | chatMessage, messageIndex |
		this.layout = HLayout.new(
			VLayout.new(
				labelStaticText = StaticText.new(),
				actionButton = Button.new(),
				nil
			),
			contentsTextView = StaticText.new(),
			nil
		);

		// Wire up functionality common to all messages.
		labelStaticText.string = chatMessage.senderName ++ ":";
		contentsTextView.string = chatMessage.contents;

		// Styleize the item based on message type.
		switch (chatMessage.type,
			\plain, {
				actionButton.visible = false;
				if ((messageIndex % 2) == 0, {
					this.background = Color.new(0.9, 0.9, 0.9);
				}, {
					this.background = Color.new(0.8, 0.8, 0.8);
				});
			},
			\director, {
				actionButton.visible = false;
			},
			\system, {
				actionButton.visible = false;
			},
			\shout, {
				actionButton.visible = false;
			},
			\code, {
				actionButton.visible = true;
				actionButton.string = "Copy";
			},
			{ "ChatItemView got unknown chatMessage.type!".postln; }
		);
	}
}
