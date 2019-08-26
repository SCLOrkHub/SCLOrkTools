SCLOrkLoginWindow {
	var callback;
	var users;
	var window;

	*new { |callback|
		^super.newCopyArgs(callback).init;
	}

	init { |callback|
		users = SCLOrkUsers.new;
		window = SCLOrkWindow.new(
			"Login to SCLOrk",
			Rect.new(0, 0, 500, 500),
		);
		window.alwaysOnTop = true;
		window.userCanClose = false;

		window.layout  = VLayout.new(

		);
	}


}