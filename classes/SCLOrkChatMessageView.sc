SCLOrkChatMessageView : View {
	const <messageViewPadding = 8.0;

	var senderNameLabel;
	var contentsTextView;

	*new { | parent, containerViewWidth, chatMessage, messageIndex |
		^super.new(parent).init(
			containerViewWidth, chatMessage, messageIndex);
	}

	init { | containerViewWidth, chatMessage, messageIndex |
		var messageWidth;

		senderNameLabel = StaticText.new(this);
		senderNameLabel.align = \topRight;
		senderNameLabel.string = chatMessage.senderName ++ ":";
		senderNameLabel.bounds = Rect.new(
			messageViewPadding, messageViewPadding,
			senderNameLabel.sizeHint.width,
			senderNameLabel.sizeHint.height);

		messageWidth = containerViewWidth -
			(messageViewPadding * 5.0) - senderNameLabel.bounds.width;

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
			},
			\shout, {
			},
			\code, {
			},
			{ "ChatItemView got unknown chatMessage.type!".postln; }
		);
	}
}
