package git.ghsmith.rrtviewer;

import java.util.Set;
import javax.ws.rs.core.Application;

@javax.ws.rs.ApplicationPath("webresources")
public class ApplicationConfig extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> resources = new java.util.HashSet<Class<?>>();
        addRestResourceClasses(resources);
        return resources;
    }

    /**
     * Do not modify addRestResourceClasses() method.
     * It is automatically populated with
     * all resources defined in the project.
     * If required, comment out calling this method in getClasses().
     */
    private void addRestResourceClasses(Set<Class<?>> resources) {
        resources.add(git.ghsmith.rrtviewer.GenericResource.class);
        resources.add(git.ghsmith.rrtviewer.JerseyMapperProvider.class);
        resources.add(git.ghsmith.rrtviewer.NormalizedHierarchyNodeResource.class);
        resources.add(git.ghsmith.rrtviewer.NormalizedHierarchyNodeResource2.class);
        resources.add(git.ghsmith.rrtviewer.NormalizedHierarchyNodeResourceEdit.class);
    }
    
}
