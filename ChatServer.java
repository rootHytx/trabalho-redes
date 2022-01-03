import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

/*
CLASSE ROOM:
  classe para definir uma sala, apenas contém o nome da sala e os users nessa sala
*/
class Room{
  public String name;
  public List<SocketChannel> usersInRoom = new ArrayList<>();
  public Room(String name){
    this.name = name;
  }
  public void add(SocketChannel sc){
    usersInRoom.add(sc);
  }
  public void remove(SocketChannel sc){
    usersInRoom.remove(sc);
  }
  public String toString(Users users){
    String result="";
    result+=name + ":\n";
    for(int i=0;i<usersInRoom.size();i++){
      result+=users.getWithSC(usersInRoom.get(i)).nick + " => " + usersInRoom.get(i) + ":\n";
    }
    return result;
  }
}

/*
CLASSE ROOMS:
  classe para definir uma lista de salas e funções adicionais para tornar comandos "join", "leave" mais fáceis
*/

class Rooms{
  public List<Room> rooms = new ArrayList<>();
  public Room find(String name){
    for(int i=0;i<rooms.size();i++){
      if(rooms.get(i).name.equals(name)) return rooms.get(i);
    }
    return null;
  }
  public String toString(Users users){
    String result="";
    for(int i=0;i<rooms.size();i++){
      result+=rooms.get(i).name + ":\n";
      for(int j=0;j<rooms.get(i).usersInRoom.size();j++){
        result+="\tUser " + users.getWithSC(rooms.get(i).usersInRoom.get(j)).nick + " => " + rooms.get(i).usersInRoom.get(j) + "\n";
      }
      result+="\n";
    }
    return result;
  }
}

/*
CLASSE USER:
  classe para definir um utilizador, AKA, um channel na socket utilizada.
  contém o nickname do user, inicialmente nulo, o socketchannel para onde enviamos mensagens específicas e
  o room em que se encontra, inicialmente nulo também. inicializamos a string nick como "" para podermos 
  comparar mais tarde e sabermos se este user não definiu ainda um nickname, não podendo entrar em
  nenhuma sala
*/

class User{
  public String nick = "";
  public SocketChannel sc;
  public int state=-1;
  public Room room;
  public User(SocketChannel sc){
    this.sc = sc;
  }
  public void leave(){
    room=null;
  }
}

/*
CLASSE USERS:
  classe para definir uma lista de utilizadores e várias funções adicionais para ajudar à procura de users
  ao longo do programa.
  add e remove servem para adicionar e remover um novo utilizador quando existe uma nova conexão
  changeNick serve para mudar o nickname de um user e garantir que a lista está atualizada
  getWithNick, getWithSC servem para, respetivamente, obter um user da lista com o nickname
  (se ele tiver um, mas esta função é importante para o comando "priv"), ou com o socketchannel 
  (muito mais utilizado pois é mais fácil e o user garantidamente tem um socketchannel,
  mas não um nickname)
*/

class Users{
  public List<User> users = new ArrayList<>();
  public void add(SocketChannel sc){
    users.add(new User(sc));
  }
  public void remove(SocketChannel sc, Users usersSuperior){
    users.remove(usersSuperior.getWithSC(sc));
  }
  public boolean changeNick(String name, SocketChannel sc, Users usersSuperior){
    User toChange = usersSuperior.getWithSC(sc);
    for(int i=0;i<users.size();i++){
      //System.out.println("EVALUATING NICK, EXISTING = " + users.get(i).nick);
      //System.out.println("EVALUATING NICK, NEW = " + name);
      if(users.get(i).nick.equals(name) && users.get(i)!=toChange) return false;
    }
    usersSuperior.getWithSC(sc).nick=name;
    return true;
  }
  public User getWithNick(String nick){
    for(int i=0;i<users.size();i++){
      if(users.get(i).nick.equals(nick)) return users.get(i);
    }
    return null;
  }
  public User getWithSC(SocketChannel sc){
    for(int i=0;i<users.size();i++){
      if(users.get(i).sc==sc) return users.get(i);
    }
    return null;
  }
  public void remove(User user){
    users.remove(user);
  }
}

public class ChatServer
{
  // A pre-allocated buffer for the received data

  static final int size = 10000; //variável para definir um tamanho
  static private ByteBuffer buffer = ByteBuffer.allocate( size );

  //inicialização de um Users, o mesmo que uma nova lista mas com funções extra que são muito úteis
  static Users users = new Users(); 

  //inicialização de um Rooms, o mesmo que uma nova lista mas com funções extra que são muito úteis
  static Rooms rooms = new Rooms();

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();


  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      //System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if (key.isAcceptable()) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            //System.out.println( "Got connection from "+s );

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ );

            users.add(sc);  //ADICIONAR NOVO USER À LISTA DE USERS
          
          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc, users.getWithSC(sc) );

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  //System.out.println( "Closing connection to "+s );
                  if(users.getWithSC(sc).room!=null){
                    Room watchers = rooms.find(users.getWithSC(sc).room.name);
                    for(int i=0;i<watchers.usersInRoom.size();i++){
                      if(watchers.usersInRoom.get(i)==sc) continue;
                      //System.out.println("CLOSING: USER IN ROOM ALREADY #" + i + " " + watchers.usersInRoom.get(i));
                      sendMessage(users.getWithSC(watchers.usersInRoom.get(i)), buffer.wrap(("LEFT " + users.getWithSC(sc).nick + " " + "\n").getBytes()));
                    }
                  }
                  remove(users.getWithSC(sc));
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { /*System.out.println( ie2 );*/ }
              if(users.getWithSC(sc).room!=null){
                Room watchers = rooms.find(users.getWithSC(sc).room.name);
                for(int i=0;i<watchers.usersInRoom.size();i++){
                  if(watchers.usersInRoom.get(i)==sc) continue;
                  //System.out.println("CLOSING: USER IN ROOM ALREADY #" + i + " " + watchers.usersInRoom.get(i));
                  sendMessage(users.getWithSC(watchers.usersInRoom.get(i)), buffer.wrap(("LEFT " + users.getWithSC(sc).nick + " " + "\n").getBytes()));
                }
              }
              remove(users.getWithSC(sc));
              //System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }
  //função para adicionar um user a uma sala, e se essa não existir, criá-la
  
  static private boolean join(String room, User toJoin){
    SocketChannel sc = toJoin.sc;
    if(toJoin.nick.equals("")) return false;
    if(room.equals("")) return false;
    for(int i=0;i<rooms.rooms.size();i++){
      if(room.equals(rooms.rooms.get(i).name)){
        rooms.rooms.get(i).usersInRoom.add(sc);
        users.users.get(users.users.indexOf(toJoin)).room=rooms.rooms.get(i);
        return true;
      }
    }
    Room newRoom = new Room(room);
    newRoom.usersInRoom.add(sc);
    rooms.rooms.add(newRoom); 
    users.users.get(users.users.indexOf(toJoin)).room=newRoom;
    return true;
  }

  static private void remove(User user){
    users.remove(user);
    if(user.room!=null) rooms.find(user.room.name).remove(user.sc);
    //System.out.println("REMOVED " + user.nick);
  }

  //função para mandar mensagem para os users de uma sala, apenas, não para todos na socket
  static private void sendMessage (Room room, ByteBuffer buffer) throws IOException {
    if(room==null) return;
    //System.out.println("USERS IN THE ROOM: " + room.usersInRoom.size());
    buffer.flip();
    buffer.clear();
    String message = decoder.decode(buffer).toString().trim();
    buffer.flip();
    for(int i=0;i<room.usersInRoom.size();i++){
      //System.out.println("USER: " + room.usersInRoom.get(i));
      sendMessage(users.getWithSC(room.usersInRoom.get(i)), buffer.wrap((message + '\n').getBytes()));
    }
  }
  static private void sendMessage (User user, ByteBuffer buffer) throws IOException {
    user.sc.write(buffer);
  }

  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc, User user) throws IOException {
    // Read the message to the buffer
    buffer.clear();
    int bytes = sc.read( buffer );
    buffer.flip();
    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }

    // Decode and print the message to stdout
    String message = decoder.decode(buffer).toString().trim();
    //if(bytes==-1) System.out.println("READ ERROR");
    //System.out.println(message);
    if(message.length()>0 && message.charAt(0)=='/') message=message.substring(1,message.length());
    if(message.split(" ")[0].trim().equalsIgnoreCase("bye")){ //comando "bye"
        if(user.room!=null){
          Room old = user.room;
          user.leave();
          rooms.find(old.name).usersInRoom.remove(sc);
          sendMessage(old, buffer.wrap(("LEFT " + user.nick + " " + "\n").getBytes()));
          sc.write(buffer.wrap((new String("BYE\n")).getBytes()));
          return false;
        }
        else{
          user.leave();
          sc.write(buffer.wrap((new String("BYE\n")).getBytes()));
          return false;
        }
    }

    if(message.split(" ")[0].trim().equalsIgnoreCase("nick")){ //comando "nick"
      //separamos por espaços e obtemos a mensagem desde o último caracter do comando até o fim
      //sendo esta string, neste caso, o novo nickname
      String name = message.substring(message.split(" ")[0].trim().length()+1,message.length()).trim();
      //System.out.println(name);
      if(name.contains(" ")){
        sc.write(buffer.wrap((new String("ERROR\n")).getBytes()));
        return true;
      }
      String old = user.nick;
      //System.out.println("OLD NICK -> " + users.getWithSC(sc).nick);
      if(!users.changeNick(name, sc, users)){ //change failed, imprime error
        //System.out.println("NEW NICK -> " + users.getWithSC(sc).nick);
        sc.write(buffer.wrap((new String("ERROR\n")).getBytes()));
        return true;
      }
      //change ok, imprime ok
      if(users.getWithSC(sc).state==-1) users.getWithSC(sc).state=0;
      //System.out.println("NEW NICK -> " + users.getWithSC(sc).nick);
      sc.write(buffer.wrap((new String("OK\n")).getBytes()));
      if(user.room!=null){
        //System.out.println("USERS IN THE ROOM: " + rooms.find(user.room.name).usersInRoom.size());
        for(int i=0;i<rooms.find(user.room.name).usersInRoom.size();i++){
          //System.out.println("USER: " + rooms.find(user.room.name).usersInRoom.get(i));
          if(rooms.find(user.room.name).usersInRoom.get(i).equals(sc)) continue;
          sendMessage(users.getWithSC(rooms.find(user.room.name).usersInRoom.get(i)), buffer.wrap(("NEWNICK " + old + " " + name + '\n').getBytes()));
        }
      }
      return true;
    }

    if(message.split(" ")[0].trim().equalsIgnoreCase("join")){ //comando "bye"
      //separamos por espaços e obtemos a mensagem desde o último caracter do comando até o fim
      //sendo esta string, neste caso, a sala
      if(message.length()<=4){
        sc.write(buffer.wrap(("ERROR\n").getBytes()));
        return true;
      }
      String room = message.substring(message.split(" ")[0].trim().length()+1,message.length()).trim();
      if(message.length()<=message.split(" ")[1].trim().length()+1 || room.equals("")){
        sc.write(buffer.wrap(("ERROR\n").getBytes()));
        return true;
      }
      Room watchers = rooms.find(room);
      if(users.getWithSC(sc).state==1){
        sendMessage(user.room, buffer.wrap(("LEFT " + user.nick + " " + "\n").getBytes()));
      }
      if(!join(room, user)){ //unable to join, nick não definido
        sc.write(buffer.wrap(("ERROR\n").getBytes()));
        return true;
      }
      users.getWithSC(sc).state=1;
      sc.write(buffer.wrap(("OK\n").getBytes()));
      if(watchers!=null){
        //System.out.println("SENDER USER: " + user.nick);
        for(int i=0;i<watchers.usersInRoom.size();i++){
          if(watchers.usersInRoom.get(i)==sc) continue;
          //System.out.println("USER IN ROOM ALREADY #" + i + " " + watchers.usersInRoom.get(i));
          //System.out.println("USER IN ROOM ALREADY #" + i + " " + watchers.usersInRoom.get(i));
          sendMessage(users.getWithSC(watchers.usersInRoom.get(i)), buffer.wrap(("JOINED " + user.nick + " " + "\n").getBytes()));
        }
      }
      return true;
    }

    if(message.split(" ")[0].trim().equalsIgnoreCase("leave")){ //comando "leave"
        if(user.state!=1){
          sc.write(buffer.wrap(("ERROR\n").getBytes()));
          return true;
        }
        Room old = user.room;
        user.leave();
        rooms.find(old.name).usersInRoom.remove(sc);
        sendMessage(old, buffer.wrap(("LEFT " + user.nick + " " + "\n").getBytes()));
        sc.write(buffer.wrap(("OK\n").getBytes()));
        users.getWithSC(sc).state=0;
        return true;
    }

    if(message.split(" ")[0].trim().equalsIgnoreCase("priv")){ //comando "priv"
        if(message.length()<=4){
          sc.write(buffer.wrap(("ERROR\n").getBytes()));
          return true;
        }
        if(user.state==-1){
          sc.write(buffer.wrap(("ERROR\n").getBytes()));
          return true;
        }
        String receiver = message.split(" ")[1].trim();
        //System.out.println(receiver + "  : " + users.getWithNick(receiver).nick);
        if(message.length()<=message.split(" ")[1].trim().length()+1 || receiver.equals("") || users.getWithNick(receiver)==null){
          sc.write(buffer.wrap(("ERROR\n").getBytes()));
          return true;
        }
        message = message.substring(message.split(" ")[1].trim().length()+1,message.length()).trim();
        //System.out.println(users.getWithNick(receiver).sc + " <- RECEIVER");
        sendMessage(users.getWithNick(receiver), buffer.wrap(("PRIV " + user.nick + " " + message + "\n").getBytes()));
        return true;
    }

    //System.out.println("after / check : " + message);
    //queremos enviar mensagem normal mas não estamos presentes em uma sala
    if(user.room==null){
      sc.write(buffer.wrap(("ERROR\n").getBytes()));
      return true;
    }
    //se tudo bem:
    //System.out.println("got to put bytes");
    sendMessage(user.room, buffer.wrap(("MESSAGE " + user.nick + " " + message + "\n").getBytes()));
    //System.out.println("wrote to buffer");
    buffer.flip();

    return true;
  }
}