SCLOrkChatMessageView : View {
	const <messageViewPadding = 9.0;

	var senderNameLabel;
	var contentsTextView;

	*new { | parent, containerViewWidth, chatMessage, messageIndex |
		^super.new(parent).init(
			containerViewWidth, chatMessage, messageIndex);
	}

	init { | containerViewWidth, chatMessage, messageIndex |
		var messageWidth, defaultBackground;

		senderNameLabel = StaticText.new(this);
		if (chatMessage.type != \system, {
		senderNameLabel.align = \topRight;
		senderNameLabel.string = chatMessage.senderName ++ ":";
		senderNameLabel.font = Font.new(Font.defaultSansFace, bold: true);
		senderNameLabel.bounds = Rect.new(
			messageViewPadding, messageViewPadding,
			senderNameLabel.sizeHint.width,
			senderNameLabel.sizeHint.height
			);
			messageWidth = containerViewWidth -
			(messageViewPadding * 5.0) - senderNameLabel.bounds.width;
		}, {
			messageWidth = containerViewWidth - (messageViewPadding * 2.0);
		});

		contentsTextView = StaticText.new(this);
		contentsTextView.fixedWidth = messageWidth;
		contentsTextView.align = \topLeft;
		contentsTextView.string = chatMessage.contents;
		contentsTextView.bounds = Rect.new(
			senderNameLabel.bounds.right + messageViewPadding,
			messageViewPadding,
			messageWidth,
			contentsTextView.sizeHint.height
		);

		this.fixedWidth = containerViewWidth - (messageViewPadding * 2);
		this.fixedHeight = max(contentsTextView.bounds.height,
			senderNameLabel.bounds.height) + (messageViewPadding * 2);

		defaultBackground = this.background;

		// Style the item based on message type.
		switch (chatMessage.type,
			\plain, {
				if ((messageIndex % 2) == 0, {
					this.background = Color.new(0.9, 0.9, 0.9);
				}, {
					this.background = Color.new(0.8, 0.8, 0.8);
				});
			},
			\director, {
			},
			\system, {
				contentsTextView.align = \center;
				contentsTextView.font = Font.new(Font.defaultSansFace,
					italic: true);
			},
			\shout, {
			},
			\code, {
				contentsTextView.font = Font.new(Font.defaultMonoFace);
			},
			{ "ChatItemView got unknown chatMessage.type!".postln; }
		);

		if (chatMessage.isEcho, {
			this.background = defaultBackground;
		});
	}
}
