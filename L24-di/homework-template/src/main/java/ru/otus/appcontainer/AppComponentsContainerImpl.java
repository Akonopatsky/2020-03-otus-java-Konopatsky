package ru.otus.appcontainer;

import ru.otus.appcontainer.api.AppComponent;
import ru.otus.appcontainer.api.AppComponentsContainer;
import ru.otus.appcontainer.api.AppComponentsContainerConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;


public class AppComponentsContainerImpl implements AppComponentsContainer {

    private final List<Object> appComponents = new ArrayList<>();
    private final Map<String, Object> appComponentsByName = new HashMap<>();

    public AppComponentsContainerImpl(Class<?> initialConfigClass) throws Exception {
        processConfig(initialConfigClass);
    }

    private void processConfig(Class<?> configClass) throws Exception {
        checkConfigClass(configClass);

        var configObject = configClass.getDeclaredConstructor().newInstance();
        System.out.println(configObject.getClass());
        List<Method> declaredMethods = new ArrayList<>(Arrays.asList(configObject.getClass().getDeclaredMethods()));
        declaredMethods.sort(Comparator.comparing(method -> method.getAnnotation(AppComponent.class).order()));
        List<Method> appMethods = declaredMethods.stream()
                .filter(method -> method.isAnnotationPresent(AppComponent.class))
                .sorted(Comparator.comparing(method -> method.getAnnotation(AppComponent.class).order()))
                .collect(Collectors.toList());

        appMethods.forEach(method -> {
            var argsTypes = new ArrayList<>(Arrays.asList(method.getParameterTypes()));

            var args = argsTypes.stream()
                    .map(this::getAppComponent)
                    .toArray();


            try {
                method.invoke(configObject, args);
                appComponentsByName.put(method.getAnnotation(AppComponent.class).name(), method.invoke(configObject, args));
                appComponents.add(method.invoke(configObject, args));
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        });

        // You code here...
    }

    private void checkConfigClass(Class<?> configClass) {
        if (!configClass.isAnnotationPresent(AppComponentsContainerConfig.class)) {
            throw new IllegalArgumentException(String.format("Given class is not config %s", configClass.getName()));
        }
    }

    @Override
    public <C> C getAppComponent(Class<C> componentClass) {
        return (C) appComponents.stream().filter(component -> componentClass.isInstance(component)).findFirst().get();
    }

    @Override
    public <C> C getAppComponent(String componentName) {
        return (C) appComponentsByName.get(componentName);
    }
}
