SCLOrkChatMessageView : View {
	const <messageViewPadding = 9.0;

	var sclorkChat;
	var senderNameLabel;
	var actionButton;
	var contentsTextView;

	*new { | parent, containerViewWidth, chatMessage, messageIndex, sclorkChat |
		^super.new(parent).init(
			containerViewWidth, chatMessage, messageIndex, sclorkChat);
	}

	init { | containerViewWidth, chatMessage, messageIndex, sclorkChat |
		var labelWidth, labelHeight, messageWidth, isPrivate;
		sclorkChat = sclorkChat;

		senderNameLabel = StaticText.new(this);
		if (chatMessage.type != \system, {
			senderNameLabel.align = \topRight;
			senderNameLabel.string = chatMessage.senderName ++ ":";
			senderNameLabel.font = Font.new(Font.defaultSansFace, bold: true);
			senderNameLabel.bounds = Rect.new(
				messageViewPadding,
				messageViewPadding,
				senderNameLabel.sizeHint.width,
				senderNameLabel.sizeHint.height);
			if (chatMessage.type == \code, {
				actionButton = Button.new(this);
				actionButton.string = "Append";
				actionButton.bounds = Rect.new(
					messageViewPadding,
					senderNameLabel.bounds.bottom + messageViewPadding,
					actionButton.sizeHint.width,
					actionButton.sizeHint.height);
				labelWidth = max(senderNameLabel.bounds.width,
					actionButton.bounds.width);
				labelHeight = actionButton.bounds.bottom;
				actionButton.action = {
					var appendString, systemChatMessage;
					appendString = "\n\n// ++ code copied from " ++
					chatMessage.senderName ++ "\n" ++ chatMessage.contents ++
					"\n// -- end of copied code\n";
					Document.current.string_(
						appendString,
						Document.current.string.size - 1,
						appendString.size
					);
					systemChatMessage = SCLOrkChatMessage.new(
						0,
						[ 0 ],
						\system,
						"Code from % appended to %.".format(
							chatMessage.senderName,
							Document.current.title));
					sclorkChat.enqueueChatMessage(systemChatMessage);
				};
			}, {
				labelWidth = senderNameLabel.bounds.width;
				labelHeight = senderNameLabel.bounds.height;
			});
			messageWidth =
			containerViewWidth - (messageViewPadding * 5.0) - labelWidth;
		}, {
			messageWidth = containerViewWidth - (messageViewPadding * 2.0);
			labelHeight = 0.0;
			labelWidth = 0.0;
		});

		contentsTextView = StaticText.new(this);
		contentsTextView.fixedWidth = messageWidth;
		contentsTextView.align = \topLeft;
		contentsTextView.string = chatMessage.contents;
		// Manipulate font before setting size, so that sizeHint will
		// be correct for the font.
		if (chatMessage.type == \system, {
			contentsTextView.align = \center;
			contentsTextView.font = Font.new(Font.defaultSansFace,
				italic: true);
		});
		if (chatMessage.type == \code, {
				contentsTextView.font = Font.new(Font.defaultMonoFace);
		});
		contentsTextView.bounds = Rect.new(
			labelWidth + (messageViewPadding * 2.0),
			messageViewPadding,
			messageWidth,
			contentsTextView.sizeHint.height
		);

		this.fixedWidth = containerViewWidth - (messageViewPadding * 2);
		this.fixedHeight = max(contentsTextView.bounds.height,
			labelHeight) + (messageViewPadding * 2);

		// Add a tooltip with recipients and darken the color for messages
		// sent only to some recipients.
		if (chatMessage.type != \system and: {
			chatMessage.recipientIds[0] != 0}, {
			this.toolTip = "Sent only to: " ++
			chatMessage.recipientNames.join(", ") ++ ".";
			isPrivate = true;
		}, {
			isPrivate = false;
		});

		// Echo messages always get plain background, as well as
		// system-type messages.
		if (chatMessage.isEcho.not, {
			if (chatMessage.type == \plain or:
				{ chatMessage.type == \code }, {
					if ((messageIndex % 2) == 0, {
						if (isPrivate, {
							this.background = Color.new(0.7, 0.7, 0.7);
						}, {
							this.background = Color.new(0.9, 0.9, 0.9);
						});
					}, {
						if (isPrivate, {
							this.background = Color.new(0.6, 0.6, 0.6);
						}, {
							this.background = Color.new(0.8, 0.8, 0.8);
						});
					});
			});
		});
	}
}
