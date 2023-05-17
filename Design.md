# Client

## Signature

- onMessage
- onConnect
- onDisconnect
- onVerify
- host
- port

## Member Variables
- private socket: Socket? - stores connection
- private unVerified: HashSet - stores sent messages that have not come back from the server
- private name: String - stores the client's name
- private room: String - stores the client's room code

## Notes

- The socket member variable is uninitialized at first, null checks need to be performed in order to
ensure a connection was made successfully
- sent messages need to be stored in a hashset in order to mark them as sent when they are
propagated back to the client from the server
- onVerify is sent a string so that it can mark the matching message as received by the server
the class implementing this client object must also store sent messages (such as when sendMessage is called)
in order to match the received message to the unverified message

## connect
    - connect to constructor provided host and port
    - initialize the socket
    - call onConnect
    - start awaitMessages with a coroutine
## close
    - send disconnect message
    - cancel any running threads using the connection
    - close the connection
    - call onDisconnect
## awaitMessages
    - lock connection with a timeout while recieving
        - timeout in order for other threads to take control periodically
    - read until null char
    - call the onMessage callback and pass the recieved message
    - check if the message is in the unverified storage, and if so call onVerify instead
## private sendMessage (Overload)
    - Handle message type ENUM
        - name change
        - room change
        - message
        - disconnect
    - Send formatted messages to server based on message type
    - non blocking
    - lock connection
## public sendMessage (Overload)
    - only send <Message></Message> formatted messages
    - otherwise the same as the private method
## changeName
    - update the private member variable
    - send the update message
## changeRoom
    - update the private member variable
    - send the update message
# Networking API
    - change name <Name>Aidan</Name>
    - change room <RoomCode>RBD23</RoomCode>
    - message <Message></Message>
    - disconnect <Disconnect>
    - messages end with a null char
    - Room codes are 5 characters, with all uppercase letters, and digits 0-9
