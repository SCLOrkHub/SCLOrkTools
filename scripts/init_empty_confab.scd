(
// Script to initialize empty Confab asset system with some basic stuff other programs assume will be present.
// Generally only needed when starting test database instances or once when Confab goes into production.
SCLOrkConfab.start;

c = Condition.new;

Routine.new({
	var userList;

	// Setup some basic people stuff
	c.test = false;
	SCLOrkConfab.createList('Users', { |listName, listId|
		userList = listId;
		c.test = true;
		c.signal;
	});
	c.wait;

	c.test = false;

}).play;

)

(
"name: \"Luke Nihlen\"
nick: \"luken\"
".parseYAML;


)