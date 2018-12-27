SCLOrkChat {
	const chatUiUpdatePeriodSeconds = 0.2;

	var nickName;
	var asDirector;
	var chatClient;
	var quitTasks;

	var window;
	var chatItemScrollView;
	var clientListView;
	var clearSelectionButton;
	var sendTextField;
	var autoScrollCheckBox;
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

	*new { | nickName, asDirector = false, chatClient = nil |
		^super.newCopyArgs(nickName, asDirector, chatClient).init;
	}

	init {
		if (chatClient.isNil, {
			chatClient = SCLOrkChatClient.new(
				NetAddr.new("sclork-server-01",
					SCLOrkChatServer.defaultListenPort));
		});
		quitTasks = false;

		this.prConstructUiElements();
		this.prConnectChatUiUpdateLogic();
		this.prConnectClientListViewUpdateLogic();

		window.front;

		chatClient.connect(nickName);
	}

	prConstructUiElements {
		var scrollCanvas;
		var windowWidth = Window.screenBounds.width / 4.0;

		// By default we occupy the right quarter of the screen.
		window = Window.new("SCLOrkChat",
			Rect.new(
				Window.screenBounds.right,
				0,
				windowWidth,
				Window.screenBounds.height),
//			resizable: false,
			border: false
		);
		window.alwaysOnTop = true;
		// window.userCanClose = false;

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
				[ messageTypeLabel = StaticText.new(), align: \right ],
				[ messageTypePopUpMenu = PopUpMenu.new(), align: \right ],
			)
		);

		scrollCanvas = View();
		scrollCanvas.layout = VLayout(nil);
		chatItemScrollView.canvas = scrollCanvas;
		chatItemScrollView.hasHorizontalScroller = false;

		clientListView.selectionMode = \multi;

		clearSelectionButton.string = "Clear Selection";
		clearSelectionButton.action = {
			clientListView.selection =  [ ];
		};

		clientListView.fixedWidth = clearSelectionButton.sizeHint.width;
		clearSelectionButton.fixedWidth = clearSelectionButton.sizeHint.width;

		autoScrollCheckBox.value = true;
		autoScrollCheckBox.string = "Auto-Scroll";
		autoScrollCheckBox.action = { | v |
			autoScroll = v.value;
		};

		messageTypeLabel.string = "Message Type:";
		messageTypePopUpMenu.items = [
			\plain,
			\code
		];

		sendTextField.action = { | v |
			// Check for nickName change first with the /nick command.
			if (v.string[0..5] == "/nick ", {
				var newName = v.string[6..];
				if (newName.size > 0, {
					("setting nickname to " ++ newName).postln;
					chatClient.nickName = newName;
				});
				v.string = "";
			}, {
				var recipientIds, chatMessage;

				if (clientListView.selection.size == 0, {
					recipientIds = [ 0 ];
				}, {
					recipientIds = clientIdList.at(clientListView.selection);
				});

				chatMessage = SCLOrkChatMessage(
					chatClient.userId,
					recipientIds,
					messageTypePopUpMenu.item,
					v.string,
					chatClient.nickName
				);
				chatClient.sendMessage(chatMessage);

				// Reset contents of text UI, and reset message type selector
				// to plain.
				v.string = "";
				messageTypePopUpMenu.value = 0;
			});
		};
	}

	enqueueChatMessage { | chatMessage |
		chatMessageQueueSemaphore.wait;
		chatMessageQueue.add(chatMessage);
		chatMessageQueueSemaphore.signal;
	}

	prConnectChatUiUpdateLogic {
		chatMessageQueue = RingBuffer.new(16);
		chatMessageQueueSemaphore = Semaphore.new(1);
		autoScroll = true;
		chatMessageIndex = 0;

		chatClient.onMessageReceived = { | chatMessage |
			this.enqueueChatMessage(chatMessage);
		};

		updateChatUiTask = SkipJack.new({
			var addedElements = false;

			chatMessageQueueSemaphore.wait;
			while ({ chatMessageQueue.size > 0 }, {
				var chatMessage, chatMessageView;

				chatMessage = chatMessageQueue.pop;
				chatMessageQueueSemaphore.signal;

				chatMessageView = SCLOrkChatMessageView.new(
					chatItemScrollView.canvas,
					chatItemScrollView.bounds.width -
					(SCLOrkChatMessageView.messageViewPadding * 2.0),
					chatMessage,
					chatMessageIndex,
					this
				);
				chatMessageIndex = chatMessageIndex + 1;
				chatItemScrollView.canvas.layout.add(chatMessageView);
				addedElements = true;

				chatMessageQueueSemaphore.wait;
			});
			chatMessageQueueSemaphore.signal;

			// Wait a short while before scrolling the view to the bottom, or the
			// new layout dimensions will not have been computed, so the view
			// won't always make it to the new bottom when it scrolls.
			if (addedElements and: { autoScroll }, {
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
			pairs = Array.new(chatClient.userDictionary.size);
			chatClient.userDictionary.keysValuesDo({ | key, value |
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
				this.prRebuildClientListView(true);
			});
		};

		chatClient.onUserChanged = { | changeType, id, nickName, oldName = nil |
			this.prRebuildClientListView(false);
			switch (changeType,
				\add, {
					this.enqueueChatMessage(SCLOrkChatMessage.new(
						chatClient.userId,
						[ chatClient.userId ],
						\system,
						"% signed in.".format(nickName)));
				},
				\remove, {
					this.enqueueChatMessage(SCLOrkChatMessage.new(
						chatClient.userId,
						[ chatClient.userId ],
						\system,
						"% signed out.".format(nickName)));
				},
				\rename, {
					this.enqueueChatMessage(SCLOrkChatMessage.new(
						chatClient.userId,
						[ chatClient.userId ],
						\system,
						"% now known as %".format(oldName, nickName)));
				},
				\timeout, {
					this.enqueueChatMessage(SCLOrkChatMessage.new(
						chatClient.userId,
						[ chatClient.userId ],
						\system,
						"% timed out.".format(nickName)));
				}
			);
		};
	}
}
