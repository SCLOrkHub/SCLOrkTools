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
		isPrivate = (chatMessage.type != \system and: { chatMessage.recipientIds[0] != 0 });
		currentHeight = messageViewPadding;
		messageWidth = containerViewWidth - (messageViewPadding * 2.0);

		// Build sender tag if needed.
		if (hasSenderTag, {
			var boundsX;
			senderNameLabel = StaticText.new(this);
			senderNameLabel.string = chatMessage.senderName ++ ":";
			senderNameLabel.font = Font.new(Font.defaultSansFace, fontSize, bold: true);
			if (chatMessage.isEcho, {
				boundsX = messageWidth - (
					senderNameLabel.sizeHint.width + messageViewPadding);
			}, {
				boundsX = messageViewPadding;
			});
			senderNameLabel.bounds = Rect.new(
				boundsX,
				messageViewPadding,
				senderNameLabel.sizeHint.width,
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

		// Echo messages always get plain background, as well as system messages.ontentsTextView.background
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

		/*
		var labelWidth, labelHeight, messageWidth, isPrivate;

		if (chatMessage.type != \system, {
			senderNameLabel = StaticText.new(this);
			senderNameLabel.align = \topRight;
			senderNameLabel.string = chatMessage.senderName ++ ":";
			senderNameLabel.font = Font.new(Font.defaultSansFace, fontSize, bold: true);
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
		contentsTextView.font = Font.new(Font.defaultSansFace, fontSize);
		contentsTextView.string = chatMessage.contents;

		// Manipulate font before setting size, so that sizeHint will
		// be correct for the font.
		if (chatMessage.type == \system, {
			contentsTextView.align = \center;
			contentsTextView.font = Font.new(Font.defaultSansFace,
				fontSize,
				italic: true);
		});
		if (chatMessage.type == \code, {
			contentsTextView.font = Font.new(Font.defaultMonoFace, fontSize);
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
			this.toolTip = "To: " ++
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
		*/
	}

	remove {
		stopShouting = true;
		super.remove;
	}
}
