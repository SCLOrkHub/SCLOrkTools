SCLOrkLoginWindow {
	var callback;
	var users;
	var window;
	var userListView;
	var loginButton;
	var userIds;

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
			userListView = ListView.new,
			HLayout.new(
				loginButton = Button.new.string_("Login")
			)
		);

		userListView.enterKeyAction = { this.prUserSelected() };
		loginButton.action = { this.prUserSelected() };

		this.prPopulateUsersList();

		window.front;
	}

	prPopulateUsersList {
		var nameMap = Dictionary.new;
		var userList;

		userIds = Array.new;
		users.userMap.keysValuesDo({ |id, dict|
			nameMap.put("% (%)".format(dict.at("name"), dict.at("short")), id);
		});

		userListView.clear;
		userList = nameMap.asSortedArray;
		userListView.items = userList.collect({|i| i[0]});
		userIds = userList.collect({|i| i[1]});
	}

	prUserSelected {
		if (userListView.value.notNil, {
			callback.value(userIds[userListView.value]);
			AppClock.sched(0, {
				window.close;
				nil;
			});
		});
	}
}