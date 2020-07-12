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

    public AppComponentsContainerPackageProcessing(Class<?> initialConfigClass) throws Exception {
        processConfig(initialConfigClass);
    }

    private void processConfig(Class<?> configClass) throws Exception {
        checkConfigClass(configClass);

        Reflections reflections = new Reflections("ru.otus");
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(AppComponentsContainerConfig.class);
        Set<Method> beanMethods = annotatedClasses.stream().
                flatMap(clazz -> getAllMethods(clazz, withAnnotation(AppComponent.class)).stream()).
                collect(Collectors.toSet());
        beanMethods.stream().forEach(bean -> System.out.println(bean));
        List<AppComponent> annotations = beanMethods.stream().
                map(method -> method.getAnnotation(AppComponent.class)).
                collect(Collectors.toList());
        annotations.stream().forEach(annotation -> System.out.println(annotation.order()));


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
