# CodeChallenge_DB
1) Created bean 'TrasferRequest' to accept request for transfer application with required validation
2) In AccountsController -> 
	- Added endpoint "/transfer"
3) In AccountService
	- Created method 'transferFunds' which will do following things
		-Validate from and to accounts
		-Acquire lock in consistent order in order to avoid deadlock
		-Complete money transfer in synchronized block
		-If transfer is successful then send notification in async manner
4) In Account Bean
	- Created method 'withdraw' which will
		- Check for validity of withdrawl and withdraw money if possible
5) Added 'updateAccount' method in repository to update latest state of the account