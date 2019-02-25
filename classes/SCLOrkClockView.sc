SCLOrkClockView : View {
	var clock;

	var beatFlasherView;
	var beatsLabel;
	var barLabel;
	var tempoLabel;
	var meterLabel;

	*new { | parent, clock |
		^super.new(parent).init(clock);
	}

	init { | clock |
		this.layout = VLayout.new(
			HLayout.new(
				StaticText.new.string_("name: " ++ clock.name),
				beatFlasherView = View.new.background_(Color.black)
			),
			HLayout.new(
				StaticText.new.string_("beats:"),
				beatsLabel = StaticText.new,
				StaticText.new.string_("bar:"),
				barLabel = StaticText.new,
			),
			HLayout.new(
				StaticText.new.string_("tempo:"),
				tempoLabel = StaticText.new,
				StaticText.new.string_("beatsPerBar:"),
				meterLabel = StaticText.new
			),
		);

		clock.play({ | beats, seconds, clock |
			AppClock.sched(0, { this.prUpdateClockUI(beats, seconds, clock) });
			1;
		});
	}



	prUpdateClockUI { | beats, seconds, clock |
		beatFlasherView.background = Color.white;
		AppClock.sched(0.1, {
			beatFlasherView.background = Color.black;
		});
		beatsLabel.string = beats.asString;
		barLabel.string = clock.bar.asString;
		tempoLabel.string = clock.tempo.asString;
		meterLabel.string = clock.beatsPerBar.asString;
	}

	remove {
		super.remove;
	}
}