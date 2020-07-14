package ru.otus.appcontainer;

import org.reflections.Reflections;
import ru.otus.appcontainer.api.AppComponent;
import ru.otus.appcontainer.api.AppComponentsContainer;
import ru.otus.appcontainer.api.AppComponentsContainerConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static org.reflections.ReflectionUtils.getAllMethods;
import static org.reflections.ReflectionUtils.withAnnotation;


public class AppComponentsContainerImpl implements AppComponentsContainer {

    private final List<Object> appComponents = new ArrayList<>();
    private final Map<String, Object> appComponentsByName = new HashMap<>();

    public AppComponentsContainerImpl(Class<?> initialConfigClass) throws Exception {
        processConfig(initialConfigClass);
    }

    public AppComponentsContainerImpl(String appPackage) throws Exception {
        processConfig(appPackage);
    }

    private void processConfig(Class<?> configClass) throws Exception {
        checkConfigClass(configClass);
        var configObject = configClass.getDeclaredConstructor().newInstance();
        Arrays.asList(configObject.getClass().getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(AppComponent.class))
                .sorted(Comparator.comparing(method -> method.getAnnotation(AppComponent.class).order()))
                .forEach(method -> initAppComponent(configObject, method));
    }

    private void processConfig(String packageName) {
        Set<ComponentInitiator> initiators = getComponentInitiators(packageName);
        createComponents(initiators);
    }

    private Set<ComponentInitiator> getComponentInitiators(String packageName) {
        Reflections reflections = new Reflections(packageName);
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(AppComponentsContainerConfig.class);
        return annotatedClasses.stream()
                .flatMap(clazz -> getAllMethods(clazz, withAnnotation(AppComponent.class)).stream()
                        .map(method -> new ComponentInitiator(clazz, method)))
                .collect(Collectors.toSet());
    }

    private void createComponents(Set<ComponentInitiator> initiators) {
        int startSize;
        do {
            startSize = initiators.size();
            initiators.removeIf(initiator -> initiator.tryInit());
        } while (startSize > initiators.size() && !initiators.isEmpty());

        if (initiators.size() > 0) {
            throw new IllegalArgumentException(String.format("Can't create %d components %s", initiators.size()));
        }
    }

    protected void initAppComponent(Object configObject, Method method) {
        var args = Arrays.asList(method.getParameterTypes()).stream()
                .map(this::getAppComponent)
                .toArray();
        try {
            Object appObject = method.invoke(configObject, args);
            String name = method.getAnnotation(AppComponent.class).name();
            appComponentsByName.put(name, appObject);
            appComponents.add(appObject);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void checkConfigClass(Class<?> configClass) {
        if (!configClass.isAnnotationPresent(AppComponentsContainerConfig.class)) {
            throw new IllegalArgumentException(String.format("Given class is not config %s", configClass.getName()));
        }
    }

    @Override
    public <C> C getAppComponent(Class<C> componentClass) {
        Optional<Object> result = appComponents.stream()
                .filter(component -> componentClass.isInstance(component))
                .findFirst();
        return (C) (result.orElse(null));
    }

    @Override
    public <C> C getAppComponent(String componentName) {
        return (C) appComponentsByName.get(componentName);
    }

    private class ComponentInitiator {
        private final Method method;
        private final Class<?>[] argsTypes;
        private Object configObject;

        private ComponentInitiator(Class<?> configClass, Method method) {
            try {
                this.configObject = configClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.method = method;
            argsTypes = method.getParameterTypes();
        }

        public boolean tryInit() {
            for (Class<?> argsType : argsTypes) {
                if (getAppComponent(argsType) == null) return false;
            }
            initAppComponent(configObject, method);
            return true;
        }
    }
}
