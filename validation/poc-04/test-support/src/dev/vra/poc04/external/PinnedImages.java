package dev.vra.poc04.external;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;
public final class PinnedImages extends ImageNameSubstitutor {
 public DockerImageName apply(DockerImageName original) {
  String name=original.asCanonicalNameString();
  String pin;
  if(name.contains("postgres")) pin="docker.io/library/postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f";
  else if(name.contains("ryuk")) pin="docker.io/testcontainers/ryuk@sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0";
  else if(name.contains("alpine")) pin="docker.io/library/alpine@sha256:8fc3dacfb6d69da8d44e42390de777e48577085db99aa4e4af35f483eb08b989";
  else throw new IllegalStateException("Unreviewed image requested; execution stopped");
  return DockerImageName.parse(pin).asCompatibleSubstituteFor(original);
 }
 protected String getDescription() { return "POC04 external reviewed digest substitution"; }
}
