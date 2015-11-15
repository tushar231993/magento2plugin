package com.magento.idea.magento2plugin.php.linemarker;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.magento.idea.magento2plugin.Magento2Icons;
import com.magento.idea.magento2plugin.xml.di.index.PluginToTypeFileBasedIndex;
import org.apache.commons.lang.WordUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by dkvashnin on 11/12/15.
 */
public class PluginLineMarkerProvider implements LineMarkerProvider {
    @Nullable
    @Override
    public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement psiElement) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> list, @NotNull Collection<LineMarkerInfo> collection) {
        PluginClassCache pluginClassCache = new PluginClassCache();
        ClassPluginCollector classPluginCollector = new ClassPluginCollector(pluginClassCache);
        MethodPluginCollector methodPluginCollector = new MethodPluginCollector(pluginClassCache);

        for (PsiElement psiElement : list) {
            if (psiElement instanceof PhpClass || psiElement instanceof Method) {
                List<? extends PsiElement> results;

                if (psiElement instanceof PhpClass) {
                    results = classPluginCollector.collect((PhpClass) psiElement);
                } else {
                    results = methodPluginCollector.collect((Method) psiElement);
                }

                if (results.size() > 0 ) {
                    NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder.
                        create(Magento2Icons.PLUGIN).
                        setTargets(results).
                        setTooltipText("Navigate Plugin");

                    collection.add(builder.createLineMarkerInfo(psiElement));
                }
            }
        }
    }

    class PluginClassCache {
        private HashMap<String, List<PhpClass>> classPluginsMap = new HashMap<String, List<PhpClass>>();

        public List<PhpClass> getPluginsForClass(PhpClass phpClass, String classFQN) {
            List<PhpClass> results = new ArrayList<>();

            if (classPluginsMap.containsKey(classFQN)) {
                return classPluginsMap.get(classFQN);
            }


            List<String[]> plugins = FileBasedIndex.getInstance()
                .getValues(
                    PluginToTypeFileBasedIndex.NAME,
                    classFQN,
                    GlobalSearchScope.allScope(phpClass.getProject())
                );

            if (plugins.size() == 0) {
                classPluginsMap.put(classFQN, results);
                return results;
            }

            PhpIndex phpIndex = PhpIndex.getInstance(phpClass.getProject());


            for (String[] pluginClassNames: plugins) {
                for (String pluginClassName: pluginClassNames) {
                    results.addAll(phpIndex.getClassesByFQN(pluginClassName));
                }
            }
            classPluginsMap.put(classFQN, results);
            return results;
        }

        public List<PhpClass> getPluginsForClass(PhpClass phpClass)
        {
            List<PhpClass> pluginsForClass = getPluginsForClass(phpClass, phpClass.getPresentableFQN());
            for (PhpClass parent: phpClass.getSupers()) {
                pluginsForClass.addAll(getPluginsForClass(parent));
            }

            return pluginsForClass;
        }

        public List<Method> getPluginMethods(PhpClass plugin) {
            List<Method> methodList = new ArrayList<Method>();
            for (Method method : plugin.getMethods()) {
                if (method.getAccess().isPublic()) {
                    String pluginMethodName = method.getName();
                    if (pluginMethodName.length() > 6) {
                        methodList.add(method);
                    }
                }
            }
            return methodList;
        }

        public List<Method> getPluginMethods(List<PhpClass> plugins) {
            List<Method> methodList = new ArrayList<Method>();
            for (PhpClass plugin: plugins) {
                methodList.addAll(getPluginMethods(plugin));
            }
            return methodList;
        }
    }
}

interface PluginCollectorI<T extends PsiElement> {
    public List<T> collect(T psiElement);
}

class ClassPluginCollector implements PluginCollectorI<PhpClass> {
    private PluginLineMarkerProvider.PluginClassCache pluginClassCache;

    public ClassPluginCollector(PluginLineMarkerProvider.PluginClassCache pluginClassCache) {
        this.pluginClassCache = pluginClassCache;
    }

    @Override
    public List<PhpClass> collect(PhpClass psiElement) {
        return pluginClassCache.getPluginsForClass(psiElement);
    }
}

class MethodPluginCollector implements PluginCollectorI<Method> {
    private PluginLineMarkerProvider.PluginClassCache pluginClassCache;

    public MethodPluginCollector(PluginLineMarkerProvider.PluginClassCache pluginClassCache) {
        this.pluginClassCache = pluginClassCache;
    }

    @Override
    public List<Method> collect(Method psiElement) {
        List<Method> results = new ArrayList<>();

        List<PhpClass> pluginsList = pluginClassCache.getPluginsForClass(psiElement.getContainingClass());
        List<Method> pluginMethods = pluginClassCache.getPluginMethods(pluginsList);

        String classMethodName = WordUtils.capitalize(psiElement.getName());
        for (Method pluginMethod: pluginMethods) {
            if (isPluginMethodName(pluginMethod.getName(), classMethodName)) {
                results.add(pluginMethod);
            }
        }
        return results;
    }

    private boolean isPluginMethodName(String pluginMethodName, String classMethodName) {
        return pluginMethodName.substring(5).equals(classMethodName) || pluginMethodName.substring(6).equals(classMethodName);
    }
}