import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

class Pair{
  public String first;
  public String second;
  public Pair(String first, String second){
    this.first = first;
    this.second = second;
  }
}

public class ChatServer
{
  // A pre-allocated buffer for the received data
  static private final int size = 10000;
  static private ByteBuffer buffer = ByteBuffer.allocate( size );
  static private List<Pair> channels = new ArrayList<>();

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
      System.out.println( "Listening on port "+port );

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
            System.out.println( "Got connection from "+s );

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ );

            channels.add(new Pair(sc.getRemoteAddress().toString().split(":")[sc.getRemoteAddress().toString().split(":").length-1], ""));

          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc );

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
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
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
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


  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc ) throws IOException {
    // Read the message to the buffer
    System.out.println("SocketChannel: " + sc.getRemoteAddress().toString().split(":")[sc.getRemoteAddress().toString().split(":").length-1]);
    for(int i=0;i<channels.size();i++){
      System.out.println(channels.get(i).first);
    }
    String commands[] = {"nick", "join", "leave", "bye", "priv"};
    buffer.clear();
    sc.read( buffer );
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }

    // Decode and print the message to stdout
    String message = decoder.decode(buffer).toString();
    System.out.println(message);
    buffer.flip();

    /*if(message.length()>0 && message.charAt(0)=='/'){
      message = message.substring(1,message.length()-1);
      System.out.println(message);
      for(int i=0;i<commands.length;i++){
        System.out.println(message.split(" ")[0]);
        System.out.println(commands[i]);
        if(message.split(" ")[0].trim().equalsIgnoreCase(commands[i])){
          isCommand=1;
          message += " -> THIS IS A COMMAND!!!!!!!!!!\n";
          System.out.println("got to put bytes as a command");
          System.out.println(message);
          sc.write(buffer.wrap(message.getBytes()));
          System.out.println("wrote to buffer as a command");
          buffer.flip();
          return true;
        }
      }
      System.out.println("after / check : " + message);
      System.out.println("got to put bytes");
      sc.write(buffer.wrap((message + "\n").getBytes()));
      System.out.println("wrote to buffer");
      buffer.flip();
      return true;
    }*/
    if(message.length()>0 && message.charAt(0)=='/'){
      message = message.substring(1,message.length()-1);
      System.out.println(message);
      if(message.split(" ")[0].trim().equalsIgnoreCase("bye")){
          sc.write(buffer.wrap((message.split(" ")[0].trim() + "\n").getBytes()));
          return false;
      }
      if(message.split(" ")[0].trim().equalsIgnoreCase("nick")){
        String name = message.substring(message.split(" ")[0].trim().length()+1,message.length());
        System.out.println(name);
        sc.write(buffer.wrap(name.getBytes()));
        return false;
      }
      System.out.println("after / check : " + message);
      System.out.println("got to put bytes");
      sc.write(buffer.wrap((message + "\n").getBytes()));
      System.out.println("wrote to buffer");
      buffer.flip();
      return true;
    }
    System.out.println("after / check : " + message);
    buffer.rewind();
    System.out.println("got to put bytes");
    sc.write(buffer);
    System.out.println("wrote to buffer");
    buffer.flip();

    return true;
  }
}
