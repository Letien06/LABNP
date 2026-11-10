package edu.vku.ftp;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Lightweight FileZilla-style Swing client for the embedded FTP service. */
public final class FtpClientFrame extends JFrame {
  private final JTextField host=new JTextField("127.0.0.1",12), port=new JTextField("2121",5), user=new JTextField("student",9);
  private final JPasswordField pass=new JPasswordField("vku2026",9);
  private final JTextField localPath=new JTextField(System.getProperty("user.dir")), remotePath=new JTextField("/");
  private final DefaultTableModel localModel=model(), remoteModel=model();
  private final JTable localTable=new JTable(localModel), remoteTable=new JTable(remoteModel);
  private final JTextArea log=new JTextArea(5,80); private final JLabel status=new JLabel("Disconnected"); private final JProgressBar progress=new JProgressBar();
  private FtpClient client;
  public FtpClientFrame(){super("Lab2 FTP · FileZilla style"); setDefaultCloseOperation(EXIT_ON_CLOSE); setMinimumSize(new Dimension(1020,650)); build(); polish(); loadLocal();}
  private static DefaultTableModel model(){return new DefaultTableModel(new Object[]{"Name","Size","Type"},0){public boolean isCellEditable(int r,int c){return false;}};}
  private void build(){
    JPanel conn=new JPanel(new FlowLayout(FlowLayout.LEFT)); conn.setBorder(BorderFactory.createTitledBorder("Quick connect"));
    conn.add(new JLabel("Host"));conn.add(host);conn.add(new JLabel("Port"));conn.add(port);conn.add(new JLabel("User"));conn.add(user);conn.add(new JLabel("Password"));conn.add(pass);
    JButton connect=new JButton("Connect"), disconnect=new JButton("Disconnect"); connect.setBackground(new Color(37,99,235)); connect.setForeground(Color.WHITE); disconnect.setBackground(new Color(220,38,38)); disconnect.setForeground(Color.WHITE); conn.add(connect);conn.add(disconnect);
    connect.addActionListener(e->connect()); disconnect.addActionListener(e->disconnect());
    JPanel panes=new JPanel(new GridLayout(1,2,8,0)); panes.setBorder(BorderFactory.createEmptyBorder(8,8,4,8)); panes.add(side("Local site",localPath,localTable,true)); panes.add(side("Remote site",remotePath,remoteTable,false));
    JButton up=new JButton("↑ Upload"), down=new JButton("↓ Download"), refresh=new JButton("↻ Refresh"); up.setBackground(new Color(22,163,74)); up.setForeground(Color.WHITE); down.setBackground(new Color(37,99,235)); down.setForeground(Color.WHITE); up.addActionListener(e->upload()); down.addActionListener(e->download()); refresh.addActionListener(e->refreshRemote());
    JPanel actions=new JPanel(new FlowLayout(FlowLayout.CENTER)); actions.add(up);actions.add(down);actions.add(refresh); progress.setPreferredSize(new Dimension(240,18)); actions.add(progress); actions.add(status);
    log.setEditable(false); log.setLineWrap(true); log.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12)); JScrollPane logs=new JScrollPane(log); logs.setBorder(BorderFactory.createTitledBorder("Message log"));
    add(conn,BorderLayout.NORTH); add(panes,BorderLayout.CENTER); add(actions,BorderLayout.SOUTH); add(logs,BorderLayout.EAST); ((BorderLayout)getContentPane().getLayout()).setHgap(5); ((BorderLayout)getContentPane().getLayout()).setVgap(5);
    UIManager.put("Table.showGrid",false); SwingUtilities.updateComponentTreeUI(this);
  }
  private JPanel side(String title,JTextField path,JTable table,boolean local){JPanel p=new JPanel(new BorderLayout(4,4));p.setBorder(BorderFactory.createTitledBorder(title)); path.setEditable(false);p.add(path,BorderLayout.NORTH); table.setRowHeight(25);table.setAutoCreateRowSorter(true);p.add(new JScrollPane(table),BorderLayout.CENTER); return p;}
  private void polish(){ Color bg=new Color(245,247,250), blue=new Color(37,99,235); getContentPane().setBackground(bg); status.setOpaque(true); status.setBorder(BorderFactory.createEmptyBorder(4,10,4,10)); status.setBackground(new Color(226,232,240)); status.setForeground(new Color(30,41,59)); progress.setForeground(blue); for(JTable t:new JTable[]{localTable,remoteTable}){t.setFont(new Font("Segoe UI",Font.PLAIN,13)); t.setRowHeight(28); t.setShowHorizontalLines(true); t.setGridColor(new Color(226,232,240));} for(Component c:getContentPane().getComponents()) if(c instanceof JPanel) c.setBackground(bg); }
  private void loadLocal(){localModel.setRowCount(0); Path p=Paths.get(localPath.getText()); try(var paths=Files.list(p)){paths.sorted().forEach(x->{try{localModel.addRow(new Object[]{x.getFileName(),Files.isDirectory(x)?"":Files.size(x),Files.isDirectory(x)?"Directory":"File"});}catch(IOException ignored){}});}catch(Exception e){log("Local: "+e.getMessage());}}
  private void connect(){try{int p=Integer.parseInt(port.getText()); client=new FtpClient(host.getText().trim(),p); client.login(user.getText().trim(),new String(pass.getPassword())); status.setText("Connected"); log("Connected to "+host.getText()+":"+p); refreshRemote();}catch(Exception e){status.setText("Connection failed");log("ERROR: "+e.getMessage());}}
  private void disconnect(){if(client!=null)client.close();client=null;status.setText("Disconnected");remoteModel.setRowCount(0);log("Disconnected");}
  private void refreshRemote(){if(client==null){log("Connect first");return;} new SwingWorker<String,Void>(){protected String doInBackground()throws Exception{return client.list();} protected void done(){try{remoteModel.setRowCount(0);for(String line:get().split("\\R")){if(line.isBlank())continue;String[] a=line.trim().split("\\s+",9);remoteModel.addRow(new Object[]{a.length>8?a[8]:line,a.length>4?a[4]:"",line.startsWith("d")?"Directory":"File"});}status.setText("Remote refreshed");}catch(Exception e){log("ERROR: "+e.getMessage());}}}.execute();}
  private String selected(JTable t){int r=t.getSelectedRow();return r<0?null:String.valueOf(t.getValueAt(r,0));}
  private void upload(){String n=selected(localTable);if(client==null||n==null){log("Select a local file and connect");return;}Path f=Paths.get(localPath.getText()).resolve(n);if(Files.isDirectory(f)){log("Directories are not supported");return;} transfer("Upload "+n,()->client.stor(n,Files.readAllBytes(f)));}
  private void download(){String n=selected(remoteTable);if(client==null||n==null){log("Select a remote file and connect");return;}Path f=Paths.get(localPath.getText()).resolve(n);transfer("Download "+n,()->Files.write(f,client.retr(n)));}
  private void transfer(String label,Task task){progress.setIndeterminate(true);status.setText(label);new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{task.run();return null;}protected void done(){progress.setIndeterminate(false);try{get();status.setText("Complete");log(label+" complete");loadLocal();refreshRemote();}catch(Exception e){status.setText("Transfer failed");log("ERROR: "+e.getCause());}}}.execute();}
  private void log(String s){log.append("["+new Date()+"] "+s+"\n");}
  @FunctionalInterface interface Task{void run()throws Exception;}
  public static void main(String[] a){SwingUtilities.invokeLater(()->{FtpClientFrame frame=new FtpClientFrame();if(a.length>0)frame.host.setText(a[0]);if(a.length>1)frame.port.setText(a[1]);frame.setSize(1220,760);frame.setVisible(true);});}
}
