(
// Script to initialize empty Confab asset system with some basic stuff other programs assume will be present.
// Generally only needed when starting test database instances or once when Confab goes into production.
SCLOrkConfab.start;

c = Condition.new;

Routine.new({
	var userList;
	var lukenImage;
	var lukenUser;
	var brunoUser;
	var chatMessageList;

	// Setup Users
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
		"name: \"Luke Nihlen\"\nshort: \"luken\"\ndirector: false", { |id|
			lukenUser = id;
			c.test = true;
			c.signal;
	});
	c.wait;
	"got % for luken id".format(lukenUser).postln;

	c.test = false;
	SCLOrkConfab.addAssetString('yaml', "bruno", userList,
		"name: \"Bruno Ruviaro\"\nshort: \"bruno\"\ndirector: true", { |id|
			brunoUser = id;
			c.test = true;
			c.signal;
	});
	c.wait;
	"got % for bruno id".format(brunoUser).postln;

	// Setup Chat Messages list
	c.test = false;
	SCLOrkConfab.createList('Chat Messages', { |listName, listId|
		chatMessageList = listId;
		c.test = true;
		c.signal;
	});
	c.wait;
	"got % for chat messages list".format(chatMessageList).postln;
}).play;
)
