SCLOrkLoginWindow {
	classvar <instance = nil;

	*new {
		if (instance.isNil, {
			instance = super.new.init;
		});
		^instance;
	}

	init {

	}
}