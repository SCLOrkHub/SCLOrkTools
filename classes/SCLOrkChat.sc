SCLOrkChat {
	const chatUiUpdatePeriodSeconds = 0.2;

	var nickName;
	var asDirector;
	var chatClient;
	var quitTasks;

	var window;
	var chatItemScrollView;
	var clientListView;
	var sendTextField;
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
				clientListView = ListView.new().fixedWidth_(windowWidth / 4.0),
			),
			HLayout.new(
				sendTextField = TextField.new()
			),
			HLayout.new(
				[ messageTypeLabel = StaticText.new(), align: \left ],
				[ messageTypePopUpMenu = PopUpMenu.new(), align: \left ],
				nil
			)
		);

		scrollCanvas = View();
		scrollCanvas.layout = VLayout(nil);
		chatItemScrollView.canvas = scrollCanvas;
		chatItemScrollView.hasHorizontalScroller = false;

		clientListView.selectionMode = \multi;

		messageTypeLabel.string = "Message Type:";
		messageTypePopUpMenu.items = [
			"plain",
			"code"
		];

		sendTextField.action = { | v |
			var chatMessage = SCLOrkChatMessage(
				chatClient.userId,
				[ 0 ],  // TODO: targeted recipients
				\plain,  // TODO: source from message type box
				v.string,
				chatClient.nickName
			);
			chatClient.sendMessage(chatMessage);
			v.string = "";
		};
	}

	prEnqueueChatMessage { | chatMessage |
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
			this.prEnqueueChatMessage(chatMessage);
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
					chatMessageIndex);
				chatMessageIndex = chatMessageIndex + 1;
				chatItemScrollView.canvas.layout.add(chatMessageView);
				addedElements = true;

				chatMessageQueueSemaphore.wait;
			});
			chatMessageQueueSemaphore.signal;

			// Wait a short while before scrolling the view to the bottom, or the
			// new layout dimensions will not have been computed, so the view won't
			// always make it to the new bottom when it scrolls.
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

	prConnectClientListViewUpdateLogic {
		clientIdList = Array.new;

		chatClient.onConnected = { | isConnected |
			if (isConnected, {
				AppClock.sched(0, {
					"building dictionary from % items".format(
						chatClient.userDictionary.size).postln;
					// The client user dictionary should now be complete,
					// so we can rebuild the clientListView.
					clientListView.clear;
					clientIdList = chatClient.userDictionary.order;
					clientListView.items = chatClient.userDictionary.atAll(
						clientIdList);
					clientListView.selection = nil;
				});
			});
		};
	}
}
