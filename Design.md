# Client

## Signature

- onMessage
- onConnect
- onDisconnect
- onVerify
- host
- port
- name
- room

## Notes

- The socket is uninitialized at first, null checks need to be performed in order to
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
- send two opening messages using the provided name and room, formatted as
    - \<Name>Aidan\</Name>
    - \<RoomCode>AAAAA\</RoomCode>

## close

- suspend function
- send disconnect message
- cancel any running threads using the connection
- close the connection
- call onDisconnect

## awaitMessages

- suspend function
- lock connection with a timeout while receiving
    - timeout in order for other threads to take control periodically
- read until null char
- call the onMessage callback and pass the received message
- check if the message is in the unverified storage, and if so call onVerify instead

## private processMessages

- handle removing the <Message></Message> tags from valid messages

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

- only send \<Message>\</Message> formatted messages
- call the private sendMessage internally
    - otherwise the same as the private method

## changeName

- lock and update the private member variable
- send the update message

## changeRoom

- lock and update the private member variable
- send the update message

# Networking API

- clients should only receive messages formatted as <Message></Message>
- Message formats
    - change name \<Name>Aidan\</Name>
    - change room \<RoomCode>RBD23\</RoomCode>
    - message \<Message>\</Message>
    - disconnect \<Disconnect\> - only sent to server, never received by client
- messages are terminated with a null char when sent over the network
- Room codes are 5 characters, with uppercase letters, and digits 0-9
- Room codes decide what messages are propagated from the server to clients when a message is received
