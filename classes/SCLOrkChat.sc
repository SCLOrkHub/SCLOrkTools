SCLOrkChat {
	const chatUiUpdatePeriodSeconds = 0.2;
	const keepLastMessageCount = 100;
	const <fontSize = 18.0;

	var name;
	var asDirector;
	var chatClient;
	var quitTasks;
	var wedged;

	var window;
	var chatItemScrollView;
	var clientListView;
	var clearSelectionButton;
	var sendTextField;
	var autoScrollCheckBox;
	var connectionStatusLabel;
	var reconnectButton;
	var messageTypeLabel;
	var messageTypePopUpMenu;

	var chatMessageQueue;
	var chatMessageQueueSemaphore;
	var autoScroll;
	var chatMessageIndex;
	var updateChatUiTask;

	// List of clientIds in the same order as the clientListView
	// list of usernames.
	var clientIdList;
	var messageViewRingBuffer;

	*new { | name, asDirector = false, chatClient = nil |
		^super.newCopyArgs(name, asDirector, chatClient).init;
	}

	init {
		if (name.isNil, {
			name = "noname";
		});
		if (chatClient.isNil, {
			chatClient = SCLOrkChatClient.new("sclork-s01.local",
				SCLOrkChatServer.defaultBindPort);
		});
		quitTasks = false;
		wedged = false;

		this.prConstructUiElements();
		this.prConnectChatUiUpdateLogic();
		this.prConnectClientListViewUpdateLogic();

		window.front;
		this.connect;
	}

	connect {
		if (wedged, {
			wedged = false;
			chatClient.prUnwedge;
			this.enqueueChatMessage(SCLOrkChatMessage.new(
				chatClient.userId,
				[ chatClient.userId ],
				\system,
				"Client unwedged."));
		});
		if (chatClient.isConnected.not, {
			chatClient.connect(name);
			AppClock.sched(0, {
				reconnectButton.visible = false;
				connectionStatusLabel.string = "connecting..";
				connectionStatusLabel.visible = true;
			});
		}, {
			this.enqueueChatMessage(SCLOrkChatMessage.new(
				chatClient.userId,
				[ chatClient.userId ],
				\system,
				"You are already connected."));
		});
	}

	free {
		quitTasks = true;
		messageViewRingBuffer.do({ | item, index |
			item.remove;
		});
		updateChatUiTask.stop;
		window.close;
		window.free;
		chatClient.free;
	}

	prConstructUiElements {
		var scrollCanvas, font;
		var windowWidth = Window.screenBounds.width / 4.0;

		// By default we occupy the right quarter of the screen.
		window = Window.new("SCLOrkChat",
			Rect.new(
				Window.screenBounds.right - (windowWidth * 1.5),
				0,
				windowWidth,
				Window.screenBounds.height * 0.75)
		);
		window.alwaysOnTop = true;
		window.userCanClose = false;

		window.layout = VLayout.new(
			HLayout.new(
				chatItemScrollView = ScrollView.new(),
				VLayout.new(
					clientListView = ListView.new(),
					clearSelectionButton = Button.new()
				)
			),
			HLayout.new(
				sendTextField = TextField.new()
			),
			HLayout.new(
				[ autoScrollCheckBox = CheckBox.new(), align: \left ],
				nil,
				[ connectionStatusLabel = StaticText.new(), align: \center ],
				[ reconnectButton = Button.new(), align: \center ],
				nil,
				[ messageTypeLabel = StaticText.new(), align: \right ],
				[ messageTypePopUpMenu = PopUpMenu.new(), align: \right ],
			)
		);

		scrollCanvas = View();
		scrollCanvas.layout = VLayout(nil);
		chatItemScrollView.canvas = scrollCanvas;
		chatItemScrollView.hasHorizontalScroller = false;

		font = Font.new(Font.defaultSansFace, fontSize);
		clientListView.selectionMode = \multi;
		clientListView.font = font;

		clearSelectionButton.string = "Send All";
		clearSelectionButton.font = font;
		clearSelectionButton.action = {
			clientListView.selection = [ ];
		};

		clientListView.fixedWidth = clearSelectionButton.sizeHint.width;
		clearSelectionButton.fixedWidth = clearSelectionButton.sizeHint.width;

		sendTextField.enabled = false;

		autoScrollCheckBox.value = true;
		autoScrollCheckBox.string = "Auto-Scroll";
		autoScrollCheckBox.font = font;
		autoScrollCheckBox.action = { | v |
			autoScroll = v.value;
		};

		connectionStatusLabel.string = "connecting..";
		connectionStatusLabel.font = font;
		connectionStatusLabel.visible = false;
		// Testing only, disable in production
		connectionStatusLabel.action = {
			this.enqueueChatMessage(SCLOrkChatMessage.new(
				chatClient.userId,
				[ chatClient.userId ],
				\system,
				"Forcing timeout."));
			chatClient.prForceTimeout;
		};

		reconnectButton.string = "Connect";
		reconnectButton.font = font;
		reconnectButton.action = { this.connect; };

		messageTypeLabel.string = "Type:";
		messageTypeLabel.font = font;
		if (asDirector, {
			messageTypePopUpMenu.items = [
				\plain,
				\code,
				\shout
			];
		}, {
			messageTypePopUpMenu.items = [
				\plain,
				\code
			];
		});
		messageTypePopUpMenu.font = font;

		sendTextField.font = font;
		sendTextField.action = { | v |
			var isCommand = false;
			var sendString = v.string;
			var sendType = messageTypePopUpMenu.item;

			if (v.string[0] == $/, {
				var commandString;
				var firstSpace = v.string.find(" ");
				if (firstSpace.isNil, {
					firstSpace = v.string.size;
				});
				commandString = v.string[0..firstSpace - 1];
				isCommand = true;
				switch (commandString,
					"/code", {
						isCommand = false;
						sendType = \code;
						sendString = sendString[firstSpace + 1..];
					},
					"/wedge", {
						wedged = true;
						chatClient.prForceTimeout;
						this.enqueueChatMessage(SCLOrkChatMessage.new(
							chatClient.userId,
							[ chatClient.userId ],
							\system,
							"Client wedged."));
					},
					"/unwedge", {
						chatClient.prUnwedge;
						this.enqueueChatMessage(SCLOrkChatMessage.new(
							chatClient.userId,
							[ chatClient.userId ],
							\system,
							"Client unwedged."));

					},
					"/name", {
						var newName = v.string[firstSpace + 1..];
						if (newName.size > 0, {
							chatClient.name = newName;
						});
					},
					"/plain", {
						isCommand = false;
						sendType = \plain;
						sendString = sendString[firstSpace + 1..];
					},
					"/quit", {
						AppClock.sched(0.1, {
							this.free;
						});
					},
					"/shout", {
						isCommand = false;
						if (asDirector, { sendType = \shout; });
						sendString = sendString[firstSpace + 1..];
					},
					{
						this.enqueueChatMessage(SCLOrkChatMessage.new(
							chatClient.userId,
							[ chatClient.userId ],
							\system,
							"Supported commands:\n" ++
							"/code <code string>\n" ++
							"/name <new name>\n" ++
							"/plain <plain string>\n" ++
							"/quit"));
					}
				);
			});

			if (isCommand.not, {
				var recipientIds, chatMessage;

				if (clientListView.selection.size == 0, {
					recipientIds = [ 0 ];
				}, {
					recipientIds = clientIdList.at(clientListView.selection);
				});

				chatMessage = SCLOrkChatMessage(
					chatClient.userId,
					recipientIds,
					sendType,
					sendString,
					chatClient.name
				);
				chatClient.sendMessage(chatMessage);

				// Reset contents of text UI, and reset message type selector
				// to plain.
				messageTypePopUpMenu.value = 0;
			});

			v.string = "";
		};
	}

	enqueueChatMessage { | chatMessage |
		chatMessageQueueSemaphore.wait;
		chatMessageQueue.add(chatMessage);
		chatMessageQueueSemaphore.signal;
	}

	prConnectChatUiUpdateLogic {
		chatMessageQueue = RingBuffer.new(16);
		messageViewRingBuffer = RingBuffer.new(keepLastMessageCount);
		chatMessageQueueSemaphore = Semaphore.new(1);
		autoScroll = true;
		chatMessageIndex = 0;

		chatClient.onMessageReceived = { | chatMessage |
			this.enqueueChatMessage(chatMessage);
		};

		updateChatUiTask = SkipJack.new({
			var addedElements = false;
			var shouldScroll = autoScroll;

			chatMessageQueueSemaphore.wait;
			while ({ chatMessageQueue.size > 0 }, {
				var chatMessage, chatMessageView;

				chatMessage = chatMessageQueue.pop;
				chatMessageQueueSemaphore.signal;

				chatMessageView = SCLOrkChatMessageView.new(
					chatItemScrollView.canvas,
					chatItemScrollView.bounds.width -
					(SCLOrkChatMessageView.messageViewPadding * 2.0),
					fontSize,
					chatMessage,
					chatMessageIndex,
					this
				);
				chatMessageIndex = chatMessageIndex + 1;
				chatItemScrollView.canvas.layout.add(chatMessageView);

				// Delete old message views, so as not to clog up our
				// UI with infinite messages.
				if (messageViewRingBuffer.size ==
					(messageViewRingBuffer.maxSize - 1), {
					var oldMessageView = messageViewRingBuffer.pop();
					oldMessageView.remove;
				});
				messageViewRingBuffer.add(chatMessageView);

				addedElements = true;
				if (chatMessage.type == '\shout', {
					shouldScroll = true;
				});

				chatMessageQueueSemaphore.wait;
			});
			chatMessageQueueSemaphore.signal;

			// Wait a short while before scrolling the view to the bottom, or the
			// new layout dimensions will not have been computed, so the view
			// won't always make it to the new bottom when it scrolls.
			if (addedElements and: { shouldScroll }, {
				AppClock.sched(chatUiUpdatePeriodSeconds / 2, {
					chatItemScrollView.visibleOrigin = Point.new(0,
						chatItemScrollView.canvas.bounds.height -
						chatItemScrollView.bounds.height
					);
				});
			});
		},
		dt: chatUiUpdatePeriodSeconds,
		stopTest: { quitTasks },
		name: "UpdateChatUiTask",
		clock: AppClock,
		autostart: true
		);
	}

	prRebuildClientListView { | clearFirst = false |
		AppClock.sched(0, {
			var selectedUserIds, pairs, names, selectedIndices;

			if (clearFirst, { clientListView.clear; });

			// Store currently selected userIds for reconstructing
			// selection after list rebuild.
			selectedUserIds = clientIdList.at(clientListView.selection).as(Set);

			// Build list of clients name, id pairs, then sort by names.
			pairs = Array.new(chatClient.nameMap.size);
			chatClient.nameMap.keysValuesDo({ | key, value |
				pairs = pairs.add([ value, key ]);
			});
			pairs.sort({ | a, b | a[0] < b[0] });

			// Split pairs list into sorted list of names and their
			// corresponding ids. This handling is designed to support
			// duplicate names with unique userIds.
			names = Array.new(pairs.size);
			clientIdList = Array.new(pairs.size);
			pairs.do({ | item, index |
				names = names.add(item[0]);
				clientIdList = clientIdList.add(item[1]);
			});

			clientListView.items = names;

			selectedIndices = Array.new(selectedUserIds.size);
			clientIdList.do({ | item, index |
				if (selectedUserIds.includes(item), {
					selectedIndices = selectedIndices.add(index);
				});
			});
			clientListView.selection = selectedIndices;
		});
	}

	prConnectClientListViewUpdateLogic {
		clientIdList = Array.new;

		chatClient.onConnected = { | isConnected |
			if (isConnected, {
				this.enqueueChatMessage(SCLOrkChatMessage.new(
					chatClient.userId,
					[ chatClient.userId ],
					\system,
					"Connected to server."));
				AppClock.sched(0, {
					connectionStatusLabel.string = "connected";
					sendTextField.enabled = true;
				});
				this.prRebuildClientListView(true);
			}, {
				this.enqueueChatMessage(SCLOrkChatMessage.new(
					chatClient.userId,
					[ chatClient.userId ],
					\system,
					"Disconnected from server."));
				AppClock.sched(0, {
					connectionStatusLabel.visible = false;
					reconnectButton.visible = true;
					sendTextField.enabled = false;
				});
			});
		};

		chatClient.onUserChanged = { | changeType, id, name, oldName = nil |
			this.prRebuildClientListView(false);
			switch (changeType,
				\add, {
					this.enqueueChatMessage(SCLOrkChatMessage.new(
						chatClient.userId,
						[ chatClient.userId ],
						\system,
						"% signed in.".format(name)));
				},
				\remove, {
					this.enqueueChatMessage(SCLOrkChatMessage.new(
						chatClient.userId,
						[ chatClient.userId ],
						\system,
						"% signed out.".format(name)));
				},
				\rename, {
					this.enqueueChatMessage(SCLOrkChatMessage.new(
						chatClient.userId,
						[ chatClient.userId ],
						\system,
						"% now known as %".format(oldName, name)));
				},
				\timeout, {
					this.enqueueChatMessage(SCLOrkChatMessage.new(
						chatClient.userId,
						[ chatClient.userId ],
						\system,
						"% timed out.".format(name)));
				}
			);
		};
	}
}
