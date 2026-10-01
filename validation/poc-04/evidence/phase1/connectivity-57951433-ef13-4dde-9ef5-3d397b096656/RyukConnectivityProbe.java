import com.github.dockerjava.api.DockerClient;
import org.testcontainers.DockerClientFactory;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class RyukConnectivityProbe {
 static String env(String k){String x=System.getenv(k);if(x==null)throw new IllegalStateException("Missing "+k);return x;}
 static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
 static String json(Object value){
  if(value==null)return "null";if(value instanceof Boolean||value instanceof Number)return value.toString();
  if(value instanceof Map<?,?> m){var j=new StringJoiner(",","{","}");for(var e:m.entrySet())j.add(json(e.getKey())+":"+json(e.getValue()));return j.toString();}
  if(value instanceof Iterable<?> l){var j=new StringJoiner(",","[","]");for(Object x:l)j.add(json(x));return j.toString();}
  return "\""+value.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";
 }
 public static void main(String[] args)throws Exception{
  Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("run_id",env("VRA_POC04_RUN_ID"));evidence.put("postgresql_started",false);
  try{
   var factory=DockerClientFactory.instance();
   require(URI.create(env("DOCKER_HOST")).equals(factory.getTransportConfig().getDockerHost()),"Selected transport differs from isolated endpoint");
   DockerClient client=factory.client();
   require(env("VRA_POC04_EXPECTED_DAEMON_ID").equals(client.infoCmd().exec().getId()),"Wrong daemon identity");
   require("127.0.0.1".equals(factory.dockerHostIpAddress()),"Wrong mapped-port host");
   var helpers=client.listContainersCmd().withShowAll(true).exec();
   require(helpers.size()==1,"Expected only Ryuk container after infrastructure initialization");
   var inspect=client.inspectContainerCmd(helpers.getFirst().getId()).exec();
   var labels=inspect.getConfig().getLabels();require(env("VRA_POC04_RUN_ID").equals(labels.get("dev.vra.run_id")),"Run label missing");
   require("true".equals(labels.get("org.testcontainers.ryuk")),"Expected Ryuk helper");
   require(inspect.getConfig().getImage().equals("docker.io/testcontainers/ryuk@sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0"),"Ryuk pin mismatch");
   require(inspect.getState().getRunning(),"Ryuk not running");
   require(inspect.getMounts().size()==1,"Unexpected helper mounts");var mount=inspect.getMounts().getFirst();
   require("/var/run/docker.sock".equals(mount.getSource())&&"/var/run/docker.sock".equals(mount.getDestination().getPath()),"Wrong isolated daemon socket mount");
   require(!mount.getSource().contains(".docker/run"),"Desktop socket present");
   var bindings=inspect.getNetworkSettings().getPorts().getBindings();int port=-1;
   for(var e:bindings.entrySet())if(e.getKey().getPort()==8080)port=Integer.parseInt(e.getValue()[0].getHostPortSpec());
   require(port>0,"Mapped Ryuk port absent");
   try(Socket socket=new Socket()){
    socket.connect(new InetSocketAddress("127.0.0.1",port),5000);socket.setSoTimeout(5000);
    var writer=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),java.nio.charset.StandardCharsets.UTF_8));
    writer.write("label=dev.vra.connectivity_probe_no_resources="+env("VRA_POC04_RUN_ID")+"\n");writer.flush();
    String ack=new BufferedReader(new InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8)).readLine();
    require("ACK".equals(ack),"Ryuk protocol acknowledgment absent");evidence.put("localhost_protocol_ack",ack);
   }
   evidence.put("daemon_id",client.infoCmd().exec().getId());evidence.put("container_id",inspect.getId());evidence.put("container_name",inspect.getName());evidence.put("created",inspect.getCreated());evidence.put("labels",labels);evidence.put("host","127.0.0.1");evidence.put("mapped_port",port);evidence.put("socket_source",mount.getSource());evidence.put("socket_destination",mount.getDestination().getPath());evidence.put("network_mode",inspect.getHostConfig().getNetworkMode());evidence.put("ryuk_enabled",true);evidence.put("result","PASS");
   Files.writeString(Path.of(args[0]),json(evidence)+"\n");System.out.println("Minimum Ryuk localhost connectivity proof PASS; exact helper ID recorded");
  }catch(Throwable failure){evidence.put("result","FAIL");evidence.put("error_type",failure.getClass().getName());evidence.put("message",failure.getMessage());Files.writeString(Path.of(args[0]),json(evidence)+"\n");throw failure;}
 }
}
