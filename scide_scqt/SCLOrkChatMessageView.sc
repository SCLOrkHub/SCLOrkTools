SCLOrkChatMessageView : View {
	const <messageViewPadding = 7.0;
	const <codeMaxHeight = 250;

	var chatMessage;
	var sclorkChat;
	var senderNameLabel;
	var contentsTextView;
	var appendView;
	var appendButton;

	var stopShouting;
	var shoutColorOn;
	var shoutTask;

	*new { | parent, containerViewWidth, fontSize, chatMessage, messageIndex, sclorkChat |
		^super.new(parent).init(
			containerViewWidth, fontSize, chatMessage, messageIndex, sclorkChat);
	}

	init { | containerViewWidth, fontSize, chatMessage, messageIndex, sclorkChat |
		var hasSenderTag, hasAppendButton, isPrivate, currentHeight, messageWidth;
		var contentHeight, codeCropped;

		chatMessage = chatMessage;
		sclorkChat = sclorkChat;

		hasSenderTag = chatMessage.type != \system;
		hasAppendButton = chatMessage.type === \code;
		isPrivate = (chatMessage.type != \system and: {
			chatMessage.recipientIds[0] != 0 });
		currentHeight = messageViewPadding;
		messageWidth = containerViewWidth - (messageViewPadding * 2.0);

		// Build sender tag if needed.
		if (hasSenderTag, {
			senderNameLabel = StaticText.new(this);
			senderNameLabel.fixedWidth = messageWidth;
			if (isPrivate, {
				var toString = chatMessage.senderName ++ " to ";
				chatMessage.recipientNames.do({ | name, index |
					toString = toString ++ name;
					if (index < (chatMessage.recipientNames.size - 1), {
						toString = toString ++ ", ";
					});
				});
				toString = toString ++ ":";
				senderNameLabel.string = toString;
			}, {
				senderNameLabel.string = chatMessage.senderName ++ ":";
			});
			senderNameLabel.font = Font.new(Font.defaultSansFace, fontSize, bold: true);
			if (chatMessage.isEcho, {
				senderNameLabel.align = \right;
				// Add a space to label string so the right-justify doesn't swallow the
				// last printing character.
				senderNameLabel.string = senderNameLabel.string ++ " ";
			});
			senderNameLabel.bounds = Rect.new(
				messageViewPadding,
				messageViewPadding,
				messageWidth,
				senderNameLabel.sizeHint.height
			);
			currentHeight = currentHeight + senderNameLabel.sizeHint.height;
		});

		// Build contents view.
		contentsTextView = StaticText.new(this);
		// Leave a little room for the scrollbar in the parent view.
		contentsTextView.fixedWidth = messageWidth - (2.0 * messageViewPadding);
		contentsTextView.string = chatMessage.contents;
		switch (chatMessage.type,
			\system, {
				contentsTextView.align = \center;
				contentsTextView.font = Font.new(Font.defaultSansFace,
					fontSize,
					italic: true);
			},
			\code, {
				contentsTextView.font = Font.new(Font.defaultMonoFace, fontSize);
			},
			{   // default
				contentsTextView.font = Font.new(Font.defaultSansFace, fontSize);
				if (chatMessage.isEcho, {
					contentsTextView.align = \right;
				});
			}
		);

		// Clip lengthy code segments so they don't blow up the chat
		if (chatMessage.type === \code, {
			if (contentsTextView.sizeHint.height > codeMaxHeight, {
				contentHeight = codeMaxHeight;
				codeCropped = true;
			}, {
				contentHeight = contentsTextView.sizeHint.height;
				codeCropped = false;
			});
			contentsTextView.fixedHeight = contentHeight;
		}, {
			contentHeight = contentsTextView.sizeHint.height;
		});
		contentsTextView.bounds = Rect.new(
			messageViewPadding,
			currentHeight,
			messageWidth - (2.0 * messageViewPadding),
			contentHeight);

		currentHeight = currentHeight + contentHeight + messageViewPadding;

		// Add append button if required.
		if (hasAppendButton, {
			var appendHeight = 0;
			appendView = CompositeView.new(this);
			if (codeCropped, {
				var continuesLabel = StaticText.new(appendView);
				continuesLabel.font = Font.new(Font.defaultSansFace,
					fontSize,
					italic: true);
				continuesLabel.string = "...";
				continuesLabel.align = \center;
				continuesLabel.bounds = Rect.new(
					0,
					0,
					messageWidth,
					continuesLabel.sizeHint.height);
				appendHeight = appendHeight + continuesLabel.sizeHint.height;
			});

			appendButton = Button.new(appendView);
			appendButton.font = Font.new(Font.defaultSansFace, fontSize);
			appendButton.string = "Append";
			appendButton.bounds = Rect.new(
				(messageWidth / 2) - (appendButton.sizeHint.width / 2),
				appendHeight,
				appendButton.sizeHint.width,
				appendButton.sizeHint.height);
			appendHeight = appendHeight + appendButton.sizeHint.height;
			appendButton.action = {
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

			appendView.bounds = Rect.new(
				messageViewPadding,
				currentHeight,
				messageWidth,
				appendHeight);
			currentHeight = currentHeight + appendHeight + messageViewPadding;
		});

		this.fixedWidth = messageWidth;
		this.fixedHeight = currentHeight;

		// Echo messages always get plain background, as well as system messages.
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
				}, {
					if (chatMessage.type == \shout, {
						stopShouting = false;
						shoutColorOn = true;
						this.acceptsMouse = true;
						this.mouseDownAction = {
							stopShouting = true;
							// Wait a short moment then freeze the color to
							// white-on-black for consistency.
							AppClock.sched(0.1, {
								this.background = Color.black;
								senderNameLabel.stringColor = Color.white;
								contentsTextView.stringColor = Color.white;
							});
							this.acceptsMouse = false;
						};
						shoutTask = SkipJack.new({
							if (shoutColorOn, {
								shoutColorOn = false;
								this.background = Color.white;
								senderNameLabel.stringColor = Color.black;
								contentsTextView.stringColor = Color.black;
							}, {
								shoutColorOn = true;
								this.background = Color.black;
								senderNameLabel.stringColor = Color.white;
								contentsTextView.stringColor = Color.white;
							});
						},
						dt: 0.5,
						stopTest: { stopShouting },
						clock: AppClock,
						autostart: true
						);
					});
			});
		});
	}

	remove {
		stopShouting = true;
		super.remove;
	}
}
