SCLOrkPD {
	var voiceNumber;

	var window;

	*new { | voiceNumber |
		^super.newCopyArgs(voiceNumber).init;
	}

	init {
		this.prConstructUIElements();

	}

	prConstructUIElements {
		window = Window.new("PublicDomain",
			Rect.new(
				0,
				0,
				800,
				600));

	}
}
