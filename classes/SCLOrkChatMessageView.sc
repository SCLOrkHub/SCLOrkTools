SCLOrkChatMessageView : View {
	var labelStaticText;
	var actionButton;
	var contentsTextView;

	*new { | chatMessage, messageIndex |
		^super.new.init(chatMessage, messageIndex);
	}

	init { | chatMessage, messageIndex |
		this.layout = HLayout.new(
			[ VLayout.new(
				[ labelStaticText = StaticText.new(), align: \topRight ],
				nil,
				[ actionButton = Button.new(), align: \bottomLeft ],
			), stretch: 0 ],
			[ contentsTextView = StaticText.new(), stretch: 10, align: \topLeft ]
		);

		// Wire up functionality common to all messages.
		labelStaticText.align = \topRight;
		labelStaticText.string = chatMessage.senderName ++ ":";
		contentsTextView.align = \topLeft;
		contentsTextView.string = chatMessage.contents;
		contentsTextView.background = Color.new(0.2, 0.2, 0.2);

		// After layout, we constrain the maximum height to be internally
		// computed minimum height of the chat view. This prevents the containing
		// view from stretching out the first few chat messages to fill the whole
		// scrollable area.
		AppClock.sched(0, {
			"view width: %, label width: %, text width: %, text hint width: %".format(
				this.bounds.width, labelStaticText.bounds.width, contentsTextView.bounds.width,
				contentsTextView.sizeHint.width
			).postln;
			// TODO: at least on my development workstation, the height difference between the
			// entire view and the height of the static text is always equal to a single line
			// of text, which we take to be the height of the sender label. Check on other
			// computers if this looks ok or no.
		    // labelStaticText.fixedWidth = labelStaticText.sizeHint.width;
			contentsTextView.minWidth = contentsTextView.sizeHint.width - labelStaticText.sizeHint.width;
			this.maxHeight = contentsTextView.sizeHint.height + labelStaticText.sizeHint.height;
		});

		// Style the item based on message type.
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
				actionButton.string = "copy";
			},
			{ "ChatItemView got unknown chatMessage.type!".postln; }
		);
	}
}
