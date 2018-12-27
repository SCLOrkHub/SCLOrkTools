(
OSCFunc.trace(false);

~loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Integer quis auctor elit sed vulputate. Sed euismod nisi porta lorem. Volutpat lacus laoreet non curabitur gravida arcu ac. Tincidunt ornare massa eget egestas purus viverra. Vitae purus faucibus ornare suspendisse sed. Ac odio tempor orci dapibus ultrices in iaculis nunc. Vulputate sapien nec sagittis aliquam malesuada bibendum arcu vitae elementum. Suspendisse sed nisi lacus sed viverra tellus. Purus in mollis nunc sed id. Phasellus vestibulum lorem sed risus ultricies. Risus nec feugiat in fermentum posuere urna nec. Nibh praesent tristique magna sit amet purus gravida quis blandit. Purus faucibus ornare suspendisse sed nisi lacus sed viverra tellus. Mi in nulla posuere sollicitudin aliquam ultrices. Dolor sit amet consectetur adipiscing elit.

Pharetra et ultrices neque ornare aenean. Nibh ipsum consequat nisl vel pretium lectus quam id leo. Mauris augue neque gravida in fermentum et sollicitudin. Iaculis at erat pellentesque adipiscing commodo. Non enim praesent elementum facilisis leo vel. Venenatis cras sed felis eget velit. Dolor morbi non arcu risus quis varius quam. Habitant morbi tristique senectus et netus et. Enim lobortis scelerisque fermentum dui faucibus in ornare quam viverra. Auctor elit sed vulputate mi sit amet mauris commodo quis.

Ut diam quam nulla porttitor massa id. Lectus arcu bibendum at varius vel pharetra vel. Quam quisque id diam vel. Nec dui nunc mattis enim. Suspendisse faucibus interdum posuere lorem ipsum dolor sit amet consectetur. Arcu non odio euismod lacinia at quis. Eu turpis egestas pretium aenean pharetra magna. Morbi quis commodo odio aenean sed adipiscing diam donec adipiscing. Morbi tristique senectus et netus et malesuada. Est pellentesque elit ullamcorper dignissim cras tincidunt lobortis feugiat. At varius vel pharetra vel turpis nunc eget lorem. Ut consequat semper viverra nam libero justo laoreet sit. Feugiat nibh sed pulvinar proin gravida hendrerit lectus. Suscipit adipiscing bibendum est ultricies integer quis auctor. Ut faucibus pulvinar elementum integer enim neque volutpat ac tincidunt. Purus non enim praesent elementum facilisis leo vel fringilla est. Suscipit adipiscing bibendum est ultricies integer quis auctor elit. Viverra mauris in aliquam sem fringilla.

Maecenas accumsan lacus vel facilisis volutpat est. Eu scelerisque felis imperdiet proin. Etiam dignissim diam quis enim lobortis scelerisque fermentum. Ipsum suspendisse ultrices gravida dictum fusce ut. Semper quis lectus nulla at volutpat diam ut venenatis tellus. Volutpat est velit egestas dui id ornare arcu odio. Vestibulum morbi blandit cursus risus at. Mattis nunc sed blandit libero volutpat sed. Congue nisi vitae suscipit tellus mauris a. Suspendisse in est ante in nibh mauris cursus mattis. Augue neque gravida in fermentum. Urna neque viverra justo nec ultrices dui sapien eget mi. Sapien faucibus et molestie ac feugiat. Rhoncus est pellentesque elit ullamcorper.

Enim praesent elementum facilisis leo vel fringilla est ullamcorper eget. In mollis nunc sed id semper risus in hendrerit. Volutpat ac tincidunt vitae semper quis lectus nulla at. Sed cras ornare arcu dui vivamus arcu felis bibendum ut. Quam vulputate dignissim suspendisse in. Viverra tellus in hac habitasse. Risus commodo viverra maecenas accumsan lacus vel facilisis. Proin gravida hendrerit lectus a. Maecenas ultricies mi eget mauris pharetra et ultrices. Varius morbi enim nunc faucibus a pellentesque. Purus sit amet volutpat consequat mauris nunc congue nisi. Quis lectus nulla at volutpat. Faucibus purus in massa tempor nec feugiat nisl pretium. In mollis nunc sed id semper risus in hendrerit gravida. Nam libero justo laoreet sit amet cursus sit. Pulvinar sapien et ligula ullamcorper malesuada. Tortor pretium viverra suspendisse potenti nullam. Arcu cursus euismod quis viverra nibh cras pulvinar. Molestie a iaculis at erat pellentesque adipiscing commodo. Ipsum dolor sit amet consectetur adipiscing elit pellentesque habitant.";

~chatServer = SCLOrkChatServer.new;
~chatUi = SCLOrkChat.new("luke", true, SCLOrkChatClient.new(
	NetAddr.new("127.0.0.1", SCLOrkChatServer.defaultListenPort), 7705));

5.do({| i |
	Task.new({
		var bot = SCLOrkChatClient.new(
			NetAddr.new("127.0.0.1", SCLOrkChatServer.defaultListenPort),
			7710 + i);
		while({ true }, {
			(30.rand).wait;
			bot.connect("bot-%-%".format(i, Main.elapsedTime.round));
			1.wait;
			10.rand.do({
				var msgType = \plain;
				var off = (~loremIpsum.size - 256).rand;
				(5.rand + 1).wait;
				if (10.rand == 0, { msgType = \shout; });
				bot.sendMessage(SCLOrkChatMessage.new(
					bot.userId,
					[ 0 ],
					msgType,
					~loremIpsum[off .. off + 256.rand])
				);
				(5.rand + 1).wait;
				if (10.rand > 7, {
					bot.nickName = "new-%-%".format(i,
						Main.elapsedTime.round);
					(10.rand + 1).wait;
				});
			});
			1.wait;
			if (10.rand == 0, {
				bot.prForceTimeout;
				30.wait;
			}, {
				bot.disconnect;
			});
		});
	}, SystemClock).start;
});
)

(
~chatUi.free;
~chatServer.free;
)

