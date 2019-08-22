SCLOrkLoginWindow {
	classvar <instance = nil;

	var userListId;

	*new {
		if (instance.isNil, {
			instance = super.new.init;
		});
		^instance;
	}

	init {
		this.prUpdateUsersList();
	}

	prUpdateUsersList {
		var c = Condition.new;
		Routine.new({}).play;
	}
}