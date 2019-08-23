(
// Script to initialize empty Confab asset system with some basic stuff other programs assume will be present.
// Generally only needed when starting test database instances or once when Confab goes into production.
SCLOrkConfab.start;

c = Condition.new;

Routine.new({
	var userList;
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


(
SCLOrkConfab.getStates({|w|
	w.keysValuesDo({|address, state|
		var user = state.asString.split($|)[0].asSymbol;
		user.postln;
	});
});
)

(
SCLOrkConfab.setUser('d961ab8e2d631abe');
)

SCLOrkConfab.idValid('nil');

nil.asSymbol;

nil.class.asSymbol === 'Symbol'
'd961ab8e2d631abe'.class.asSymbol === 'Symbol'
(
SCLOrkConfab.start;
Routine.new({
	var c = SCLOrkChatClient.new;
	3.wait;
	c.userMap.postln;
}).play;
)
