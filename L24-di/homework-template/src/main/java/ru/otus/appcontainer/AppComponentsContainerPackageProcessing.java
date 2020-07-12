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


public class AppComponentsContainerPackageProcessing implements AppComponentsContainer {

    private final List<Object> appComponents = new ArrayList<>();
    private final Map<String, Object> appComponentsByName = new HashMap<>();

    public AppComponentsContainerPackageProcessing(String appPackage) throws Exception {
        processConfig(appPackage);
    }

    private void processConfig(String appPackage) throws Exception {
        Reflections reflections = new Reflections(appPackage);
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(AppComponentsContainerConfig.class);
        Set<DiyBean> beans = annotatedClasses.stream()
                .flatMap(clazz -> getAllMethods(clazz, withAnnotation(AppComponent.class))
                        .stream()
                        .map(method -> new DiyBean(clazz, method)))
                .collect(Collectors.toSet());
        Iterator<DiyBean> iterator = beans.iterator();
        int startSize;
        do {
            startSize = beans.size();
            beans.removeIf(bean -> bean.tryInit());
        } while (startSize > beans.size());

        if (beans.size() > 0)
            throw new IllegalArgumentException(String.format("Can not initialise %d beans", beans.size()));
    }

    private void initAppComponents(Object configObject, Method method) {
        var args = Arrays.asList(method.getParameterTypes()).stream()
                .map(this::getAppComponent)
                .toArray();
        try {
            method.invoke(configObject, args);
            String name = method.getAnnotation(AppComponent.class).name();
            Object appObject = method.invoke(configObject, args);
            appComponentsByName.put(name, appObject);
            appComponents.add(appObject);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public <C> C getAppComponent(Class<C> componentClass) {
        Optional<Object> result = appComponents.stream().filter(component -> componentClass.isInstance(component)).findFirst();
        return (C) (result.isPresent() ? result.get() : null);
    }

    @Override
    public <C> C getAppComponent(String componentName) {
        return (C) appComponentsByName.get(componentName);
    }

    private class DiyBean {
        private final Method method;
        private final Class<?>[] argsTypes;
        private Object configObject;
        private Object[] args;
        private boolean isReady = false;

        private DiyBean(Class<?> configClass, Method method) {
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

        private void initAppComponent(Object configObject, Method method) {
            var args = Arrays.asList(method.getParameterTypes()).stream()
                    .map(AppComponentsContainerPackageProcessing.this::getAppComponent)
                    .toArray();
            try {
                method.invoke(configObject, args);
                String name = method.getAnnotation(AppComponent.class).name();
                Object appObject = method.invoke(configObject, args);
                appComponentsByName.put(name, appObject);
                appComponents.add(appObject);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

}
