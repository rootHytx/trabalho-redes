import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    private String server;
    private int port;
    private BufferedReader inFromUser;
    private Socket clientSocket;
    private DataOutputStream outToServer;
    private BufferedReader inFromServer;


    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
        this.server=server;
        this.port=port;
    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
        System.out.println(message);
        if(message.charAt(0)=='/') message=message.substring(1,message.length());
        String serverMessage;
        byte[] send = (message + '\n').getBytes();
        outToServer.write(send);
        System.out.println("wrote to server");
    }
    public void fromServer(String serverMessage) throws IOException {
        System.out.println("read from server");
        System.out.println(serverMessage);
        if(serverMessage.split(" ")[0].trim().equalsIgnoreCase("bye")) System.exit(0);
        if(serverMessage.split(" ")[0].trim().equalsIgnoreCase("message")){
            String name = serverMessage.split(" ")[1].trim();
            serverMessage = serverMessage.substring(name.length()+7, serverMessage.length())+'\n';
            System.out.println(serverMessage);
            printMessage(name + ": " + serverMessage);
            return;
        }
        serverMessage += "\n";
        System.out.println(serverMessage);
        printMessage(serverMessage);
        return;
    }

    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
        clientSocket = new Socket(server, port);
        inFromUser = new BufferedReader(new InputStreamReader(System.in));
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
        inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        Thread newThread = new Thread(() -> {
            while(true){
                try {
                  fromServer(inFromServer.readLine());
                } catch( IOException ie ) {  }
            }
        });
        newThread.start();
    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}

