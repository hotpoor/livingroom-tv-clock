package cn.livingroom.flipclock;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.*;
import android.util.Base64;

/** WebSocket invalidations only. Every connection and invalidation requests HTTP state. */
final class InventoryEvents {
    private static boolean started;
    static synchronized void start(Context context) {
        if(started)return;started=true;
        final Context app=context.getApplicationContext();
        new Thread(()->{while(true){
            try{run(app);}catch(Exception ignored){}
            try{Thread.sleep(3000);}catch(InterruptedException e){return;}
        }},"inventory-events").start();
    }
    private static String line(InputStream in)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();int c;
        while((c=in.read())!=-1){if(c==10)return b.toString("UTF-8").replace("\r","");if(b.size()>8192)throw new IOException();b.write(c);}throw new EOFException();
    }
    private static byte[] read(InputStream in,int length)throws IOException{
        byte[] b=new byte[length];int n=0,k;while(n<length){k=in.read(b,n,length-n);if(k<0)throw new EOFException();n+=k;}return b;
    }
    private static void pong(OutputStream out,byte[] body)throws IOException{
        byte[] mask=new byte[4];new SecureRandom().nextBytes(mask);out.write(0x8a);out.write(0x80|body.length);out.write(mask);for(int i=0;i<body.length;i++)out.write(body[i]^mask[i%4]);out.flush();
    }
    private static void run(Context app)throws Exception{
        JSONObject config=InventorySync.config(app);URL url=new URL(config.getString("url"));
        // The paired service is plain HTTP on the home LAN.
        if(!"http".equals(url.getProtocol()))throw new IOException("unsupported protocol");
        int port=url.getPort()>0?url.getPort():80;
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(url.getHost(),port),4000);socket.setSoTimeout(30000);
            InputStream in=new BufferedInputStream(socket.getInputStream());OutputStream out=socket.getOutputStream();
            byte[] nonce=new byte[16];new SecureRandom().nextBytes(nonce);String key=Base64.encodeToString(nonce,Base64.NO_WRAP);
            String request="GET /api/events HTTP/1.1\r\nHost: "+url.getHost()+":"+port+"\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: "+key+"\r\nAuthorization: Bearer "+config.getString("token")+"\r\n\r\n";
            out.write(request.getBytes("UTF-8"));out.flush();if(!line(in).contains(" 101 "))throw new IOException("upgrade failed");
            String expected=Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")),Base64.NO_WRAP);boolean valid=false;String header;
            while(!(header=line(in)).isEmpty())if(header.toLowerCase(java.util.Locale.ROOT).startsWith("sec-websocket-accept:"))valid=header.substring(header.indexOf(':')+1).trim().equals(expected);
            if(!valid)throw new IOException("invalid upgrade");
            while(true){byte[] head=read(in,2);int op=head[0]&15,length=head[1]&127;if((head[0]&128)==0||(head[1]&128)!=0||length>125)throw new IOException("invalid frame");byte[] body=read(in,length);
                if(op==8)return;if(op==9){pong(out,body);continue;}
                if(op==1){String type=new JSONObject(new String(body,"UTF-8")).optString("type");if(type.equals("connected")||type.equals("inventory_changed"))InventorySync.request(app,null);}
            }
        }
    }
}
