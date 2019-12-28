SCLOrkCDTUIClockView : View {
	var beat;
	var localTime;
	var serverTime;
	var timeDiff;

	*new { |ip, diag|
		^super.new.init(ip, diag);
	}

	init { |ip, diag|
		this.layout = HLayout(
			StaticText.new.string_(ip.asString),
			StaticText.new.string_("beats:"), beat = StaticText.new,
			StaticText.new.string_("local time:"), localTime = StaticText.new,
			StaticText.new.string_("server time:"), serverTime = StaticText.new,
			StaticText.new.string_("time diff:"), timeDiff = StaticText.new
		);
		this.updateClockView(diag);
	}

	updateClockView { |diag|
		this.background = Color.black;
		beat.string = diag.at(\beat).round(0.1);
		beat.stringColor = Color.white;
		localTime.string = diag.at(\localTime).round(0.1);
		localTime.stringColor = Color.white;
		serverTime.string = diag.at(\serverTime).round(0.1);
		serverTime.stringColor = Color.white;
		timeDiff.string = diag.at(\timeDiff).round(0.1);
		timeDiff.stringColor = Color.white;
		AppClock.sched(0.1, {
			this.background = Color.white;
			beat.stringColor = Color.black;
			localTime.stringColor = Color.black;
			serverTime.stringColor = Color.black;
			timeDiff.stringColor = Color.black;
		});
	}
}

SCLOrkCDTUICohortView : View {
	var addrViewMap;

	*new { |cohortName|
		^super.new.init(cohortName);
	}

	init { |cohortName|
		addrViewMap = IdentityDictionary.new;
		this.layout = VLayout(
			StaticText.new(this).string_(cohortName.asString)
		);
	}

	clockTick { |time, diag|
		var ip = diag.at(\address).ip.asSymbol;
		if (addrViewMap.includesKey(ip), {
			addrViewMap.at(ip).updateClockView(diag);
		}, {
			var view = SCLOrkCDTUIClockView.new(ip, diag);
			this.layout.add(view);
			addrViewMap.put(ip, view);
		});
	}
}

SCLOrkCDTUI {
	var cdt;
	var window;
	var cohortViewMap;

	*new {
		^super.new.init;
	}

	init {
		cohortViewMap = IdentityDictionary.new;
		cdt = SCLOrkCDT.new({ |time, diag| this.prDiagCallback(time, diag); });
		this.prConstructUIElements;
	}

	prConstructUIElements {
		window = SCLOrkWindow.new("SCLOrkCDTUI", Rect.new(0, 0, 800, 600));
		window.layout = VLayout.new();
		window.front;
	}

	prDiagCallback { |time, diag|
		AppClock.sched(0, {
			var cohortName = diag.at(\cohortName);
			if (cohortViewMap.includesKey(cohortName).not, {
				var view = SCLOrkCDTUICohortView.new(cohortName);
				window.layout.add(view);
				cohortViewMap.put(cohortName, view);
			});
			cohortViewMap.at(cohortName).clockTick(time, diag);
		});
	}
}
