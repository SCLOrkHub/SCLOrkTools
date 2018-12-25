SCLOrkChat {
	const chatUiUpdatePeriodSeconds = 0.2;

	var nickName;
	var asDirector;
	var chatClient;
	var quitTasks;

	var window;
	var chatItemScrollView;
	var sendTextField;

	var chatMessageQueue;
	var chatMessageQueueSemaphore;
	var autoScroll;
	var chatMessageIndex;
	var updateChatUiTask;

	*new { | nickName, asDirector = false, chatClient = nil |
		^super.newCopyArgs(nickName, asDirector, chatClient).init;
	}

	init {
		if (chatClient.isNil, {
			chatClient = SCLOrkChatClient.new(
				NetAddr.new("sclork-server-01", SCLOrkChatServer.defaultListenPort));
		});
		quitTasks = false;

		this.prConstructUiElements();
		this.prConnectChatUiUpdateLogic();
		window.front;

		chatClient.connect(nickName);
	}

	prConstructUiElements {
		var scrollCanvas;

		// By default we occupy the right quarter of the screen.
		window = Window.new("SCLOrkChat",
			Rect.new(Window.screenBounds.right, 0,
				Window.screenBounds.width / 4,
				Window.screenBounds.height)
		);
		window.alwaysOnTop = true;
		// window.userCanClose = false;
		window.layout = VLayout.new(
			HLayout.new(
				chatItemScrollView = ScrollView.new()
			),
			HLayout.new(
				sendTextField = TextField.new()
			)
		);

		scrollCanvas = View();
		scrollCanvas.layout = VLayout(nil);
		chatItemScrollView.canvas = scrollCanvas;
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

				chatMessageView = SCLOrkChatMessageView.new(chatMessage, chatMessageIndex);
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
}
