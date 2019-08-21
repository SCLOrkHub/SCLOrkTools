(
// Script to initialize empty Confab asset system with some basic stuff other programs assume will be present.
// Generally only needed when starting test database instances or once when Confab goes into production.
SCLOrkConfab.start;

c = Condition.new;

Routine.new({
	var userList;
	var lukenUser;
	var brunoUser;

	// Setup some basic people stuff
	c.test = false;
	SCLOrkConfab.createList('Users', { |listName, listId|
		userList = listId;
		c.test = true;
		c.signal;
	});
	c.wait;

	"got % for Users list Id".format(userList).postln;

	c.test = false;
	SCLOrkConfab.addAssetString('yaml', "luken", userList,
		"name: \"Luke Nihlen\"\nnick: \"luken\"", { |id|
			lukenUser = id;
			c.test = true;
			c.signal;
	});
	c.wait;

	"got % for luken id".format(lukenUser).postln;

	c.test = false;
	SCLOrkConfab.addAssetString('yaml', "bruno", userList,
		"name: \"Bruno Ruviaro\"\nnick: \"bruno\"", { |id|
			brunoUser = id;
			c.test = true;
			c.signal;
	});
	c.wait;

	"got % for bruno id".format(brunoUser).postln;
}).play;
)
