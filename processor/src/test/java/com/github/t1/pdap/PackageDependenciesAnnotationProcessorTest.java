package com.github.t1.pdap;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

class PackageDependenciesAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    private void compileSource(String source) {
        compile(
            packageInfo("source", "target"),
            file("source/Source.java", source),

            packageInfo("target"),
            targetInterface());
    }

    private void compileForbiddenSource(String source) {
        compile(
            packageInfo("source"),
            file("source/Source.java", source),

            packageInfo("target"),
            targetInterface());
    }

    private StringJavaFileObject packageInfo(String packageName, String... dependencies) {
        return file(packageName.replace('.', '/') + "/package-info.java", "" +
            "@AllowDependenciesOn(" + dependenciesString(dependencies) + ")\n" +
            "package " + packageName + ";\n" +
            "\n" +
            "import com.github.t1.pdap.AllowDependenciesOn;\n");
    }

    private String dependenciesString(String... dependencies) {
        switch (dependencies.length) {
            case 0:
                return "";
            case 1:
                return "\"" + dependencies[0] + "\"";
            default:
                return Stream.of(dependencies).collect(joining("\", \"", "{\"", "\"}"));
        }
    }

    private StringJavaFileObject targetInterface() {
        return file("target/Target.java", "" +
            "package target;\n" +
            "\n" +
            "public interface Target {\n" +
            "}\n");
    }

    private StringJavaFileObject targetClass() {
        return file("target/Target.java", "" +
            "package target;\n" +
            "\n" +
            "public class Target {\n" +
            "}\n");
    }

    private StringJavaFileObject targetAnnotation() {
        return file("target/Target.java", "" +
            "package target;\n" +
            "\n" +
            "public @interface Target {\n" +
            "    String value();\n" +
            "}\n");
    }

    private StringJavaFileObject targetEnum() {
        return file("target/Target.java", "" +
            "package target;\n" +
            "\n" +
            "public enum Target {\n" +
            "    FOO\n" +
            "}\n");
    }


    @Nested class BasicCompilerTests {
        @Test void shouldSimplyCompile() {
            compile(file("Simple.java", "" +
                "public class Simple {\n" +
                "}"));

            expect();
        }

        @Test void shouldReportErrorForUnknownSymbol() {
            compile(file("Failing.java", "" +
                "@UnknownAnnotation\n" +
                "public class Failing {\n" +
                "}"));

            expect(
                warning("compiler.warn.proc.annotations.without.processors", "No processor claimed any of these annotations: UnknownAnnotation"),
                error("/Failing.java", 1, 1, 18, 1, 2,
                    "compiler.err.cant.resolve", "cannot find symbol\n  symbol: class UnknownAnnotation"));
        }

        @Test void shouldNotReportErrorAboutMethodInvocationWithOnJavaLangType() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    String value;\n" +
                    "    public void foo() { this.value.isEmpty(); }\n" +
                    "}\n"));

            expect();
        }
    }

    @Nested class AllowDependenciesOnTests {
        @Test void shouldReportErrorForInvalidDependencies() {
            compile(
                packageInfo("source", "undefined"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "}\n"));

            expect(
                error("/source/package-info.java", 0, 0, 97, 1, 1,
                    "compiler.err.proc.messager", "Invalid @AllowDependenciesOn: unknown package [undefined]")
            );
        }

        @Test void shouldReportErrorForInvalidSuperDependencies() {
            compile(
                packageInfo("source", "undefined"),
                packageInfo("source.sub"),
                file("source/sub/Source.java", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "}\n"));

            expect(
                error("/source/package-info.java", 0, 0, 97, 1, 1,
                    "compiler.err.proc.messager", "Invalid @AllowDependenciesOn: unknown package [undefined]")
            );
        }

        @Test void shouldWarnAboutMissingDependencies() {
            compile(
                file("source/package-info.java", "" +
                    "package source;"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect(
                warning("/source/package-info.java", 0, 0, 15, 1, 1,
                    "compiler.warn.proc.messager", "no @AllowDependenciesOn annotation")
            );
        }

        @Test void shouldNotWarnAboutMissingSuperDependencies() {
            compile(
                packageInfo("source.sub", "target"),
                file("source/sub/Source.java", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect();
        }

        @Test void shouldNotWarnAboutMissingSuperPackageInfo() {
            compile(
                packageInfo("source.sub1.sub2", "target"),
                file("source/sub1/sub2/Source.java", "" +
                    "package source.sub1.sub2;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetClass());

            expect();
        }

        @Test void shouldReportErrorForDependencyOnSelf() {
            compile(
                packageInfo("source", "source", "target"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "   private Target target;" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect(
                error("/source/package-info.java", 0, 0, 106, 1, 1,
                    "compiler.err.proc.messager", "Cyclic dependency declared on [source]")
            );
        }

        @Test void shouldWarnAboutUnusedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "}\n");

            expect(
                warning("/source/package-info.java", 0, 0, 94, 1, 1,
                    "compiler.warn.proc.messager", "Unused dependency on [target]")
            );
        }
    }

    @Nested class ImportedDependencies {
        @Test void shouldNotReportErrorForFieldWithAllowedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorForFieldWithForbiddenDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n");

            expect(
                error("/source/Source.java", 81, 66, 88, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForSecondClassInCompilationUnitWithFieldWithForbiddenDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {}\n" +
                "\n" +
                "class SubSource {\n" +
                "    private Target target;\n" +
                "}\n");

            expect(
                error("/source/Source.java", 101, 86, 108, 8, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForEnumFieldWithForbiddenDependency() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetEnum());

            expect(
                error("/source/Source.java", 81, 66, 88, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForImportedStaticEnumValueWithForbiddenDependency() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import static target.Target.FOO;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Object target = FOO;\n" +
                    "}\n"),

                packageInfo("target"),
                targetEnum());

            expect(
                error("/source/Source.java", 92, 77, 105, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Disabled @Test void shouldReportErrorForWildcardImportedStaticEnumValueWithForbiddenDependency() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import static target.Target.*;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Object target = FOO;\n" +
                    "}\n"),

                packageInfo("target"),
                targetEnum());

            expect(
                error("/source/Source.java", 92, 77, 105, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForFieldWithForbiddenDependencyOnNonCompiledClass() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.List;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private List<?> list;\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 83, 67, 88, 6, 21,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }

        @Test void shouldReportErrorForFieldArgWithForbiddenDependencyOnNonCompiledClass() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Enum<Target> list;\n" +
                    "}\n"),

                packageInfo("target"),
                targetEnum());

            expect(
                error("/source/Source.java", 87, 66, 92, 6, 26,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForNonCompiledFieldTypeArgWithForbiddenDependencyOnNonCompiledClass() {
            compile(
                packageInfo("source", "java.util"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.List;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private List<BigInteger> list;\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 121, 96, 126, 7, 30,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.math]")
            );
        }

        @Test void shouldReportErrorForNonCompiledFieldExtendsTypeArgWithForbiddenDependencyOnNonCompiledClass() {
            compile(
                packageInfo("source", "java.util"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.List;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private List<? extends BigInteger> list;\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 131, 96, 136, 7, 40,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.math]")
            );
        }

        @Test void shouldReportErrorForNonCompiledFieldSuperTypeArgWithForbiddenDependencyOnNonCompiledClass() {
            compile(
                packageInfo("source", "java.util"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.List;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private List<? super BigInteger> list;\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 129, 96, 134, 7, 38,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.math]")
            );
        }

        @Test void shouldReportErrorForFieldValueWithForbiddenDependency() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Object target = new Target();\n" +
                    "}\n"),

                packageInfo("target"),
                targetClass());

            expect(
                error("/source/Source.java", 81, 66, 103, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorAboutForbiddenFieldAnnotation() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private @Target(\"t\") String target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetAnnotation());

            expect(
                warning("compiler.warn.proc.annotations.without.processors", "No processor claimed any of these annotations: target.Target")
            );
        }

        @Test void shouldReportErrorAboutForbiddenMethodInvocationWithoutArguments() {
            compile(
                packageInfo("source", "target1"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {\n" +
                    "        new Target1().bar();\n" +
                    "    }\n" +
                    "}\n"),

                packageInfo("target1", "target2"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public Target2 bar() { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 68, 123, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenStaticMethodInvocation() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {\n" +
                    "        Target.bar();\n" +
                    "    }\n" +
                    "}\n"),

                packageInfo("target"),
                file("target/Target.java", "" +
                    "package target;\n" +
                    "\n" +
                    "public class Target {\n" +
                    "    public static void bar() {}\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 79, 66, 114, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenStaticMethodInvocationToNonCompiledClassSameType() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.Arrays;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {" +
                    "        Arrays.asList((Object) \"bar\");\n" +
                    "    }\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 82, 69, 133, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }

        @Disabled @Test void shouldReportErrorAboutForbiddenStaticMethodInvocationToNonCompiledClassSuperType() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.Arrays;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {" +
                    "        Arrays.asList(\"bar\");\n" +
                    "    }\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 82, 69, 133, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }

        @Test void shouldNotReportErrorAboutAllowedAnonymousSubclassInMethodBody() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private void foo() { Object target = new Target() {}; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorAboutForbiddenAnonymousSubclassInMethodBody() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private void foo() { Object target = new Target() {}; }\n" +
                "}\n");

            expect(
                error("/source/Source.java", 79, 66, 121, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorAboutAllowedMethodReturnType() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;" +
                "\n" +
                "public class Source {\n" +
                "    private Target foo() { return null; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorAboutForbiddenMethodReturnType() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;" +
                "\n" +
                "public class Source {\n" +
                "    private Target foo() { return null; }\n" +
                "}\n");

            expect(
                error("/source/Source.java", 80, 65, 102, 5, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorForEnumWithAllowedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "enum Source {\n" +
                "    FOO;\n" +
                "    private Target target;\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorForEnumWithForbiddenDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "enum Source {\n" +
                "    FOO;\n" +
                "    private Target target;\n" +
                "}\n");

            expect(
                error("/source/Source.java", 82, 67, 89, 7, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorForAnnotationWithAllowedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public @interface Source {\n" +
                "    Class<Target> value();\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorForAnnotationWithForbiddenDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public @interface Source {\n" +
                "    Class<Target> value();\n" +
                "}\n");

            expect(
                error("/source/Source.java", 85, 71, 93, 6, 19,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorForInterfaceWithAllowedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public interface Source extends Target {\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorForInterfaceWithForbiddenDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public interface Source extends Target {\n" +
                "}\n");

            expect(
                error("/source/Source.java", 47, 40, 82, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldWarnAboutClassWithUnusedImport() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source{\n" +
                "}\n");

            expect(
                warning("/source/Source.java", 47, 40, 62, 5, 8,
                    "compiler.warn.proc.messager", "Import [target] not found as dependency"),
                warning("/source/package-info.java", 0, 0, 94, 1, 1,
                    "compiler.warn.proc.messager", "Unused dependency on [target]")
            );
        }

        @Test void shouldReportErrorForForbiddenExtendsClassDependency() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source extends Target {\n" +
                    "}\n"),

                packageInfo("target"),
                targetClass());

            expect(
                error("/source/Source.java", 47, 40, 78, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForForbiddenExtendsGenericClassDependency() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source extends Generic<Target> {}\n" +
                    "\n" +
                    "class Generic<T> {}\n"),

                packageInfo("target"),
                targetClass());

            expect(
                error("/source/Source.java", 47, 40, 86, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForForbiddenExtendsInterfaceDependency() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public interface Source extends Target {\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect(
                error("/source/Source.java", 47, 40, 82, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForForbiddenImplementsDependency() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source implements Target {\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect(
                error("/source/Source.java", 47, 40, 81, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForForbiddenSingleTypedImplementsDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source implements Generic<Target> {}\n" +
                "\n" +
                "interface Generic<T> {}\n");

            expect(
                error("/source/Source.java", 47, 40, 89, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForForbiddenSecondTypedImplementsDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source implements Generic<String, Target> {}\n" +
                "\n" +
                "interface Generic<T, U> {}\n");

            expect(
                error("/source/Source.java", 47, 40, 97, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorsForTwoForbiddenAndOneAllowedFieldDependency() {
            compile(
                packageInfo("source", "target3"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1a;\n" +
                    "import target1.Target1b;\n" +
                    "import target2.Target2;\n" +
                    "import target3.Target3;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target1a t1a;\n" +
                    "    private Target1b t1b;\n" +
                    "    private Target2 t2;\n" +
                    "    private Target3 t3;\n" +
                    "}\n"),

                packageInfo("target1"),
                file("target1/Target1a.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "public class Target1a {\n" +
                    "}\n"),
                file("target1/Target1b.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "public class Target1b {\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"),

                packageInfo("target3"),
                file("target3/Target3.java", "" +
                    "package target3;\n" +
                    "\n" +
                    "public class Target3 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 159, 142, 163, 9, 22,
                    "compiler.err.proc.messager", "Forbidden dependency on [target1]"),
                error("/source/Source.java", 210, 194, 213, 11, 21,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorForForbiddenTypeBoundDependency() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source<T extends Target> {\n" +
                    "}\n"),

                packageInfo("target"),
                targetClass());

            expect(
                error("/source/Source.java", 47, 40, 81, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorForDependencyToAnnotation() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "@Target(\"/source\")" +
                    "public class Source {\n" +
                    "}\n"),

                packageInfo("target"),
                targetAnnotation());

            expect(
                warning("compiler.warn.proc.annotations.without.processors", "No processor claimed any of these annotations: target.Target")
            );
        }
    }

    @Nested class QualifiedDependencies {
        @Test void shouldNotReportErrorAboutAllowedQualifiedFieldType() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private target.Target target;\n" +
                "}\n");

            expect();
        }

        @Test void shouldNotReportErrorAboutAllowedQualifiedMethodReturnType() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private target.Target foo() { return null; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldNotReportErrorAboutAllowedQualifiedVariableInMethodBody() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private void foo() { target.Target target = null; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldNotReportErrorAboutAllowedQualifiedAnonymousSubclassInMethodBody() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private void foo() { Object target = new target.Target() {}; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldNotReportErrorAboutAllowedQualifiedAnonymousSubclassField() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Object target = new target.Target() {};\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorAboutForbiddenQualifiedStaticVararg0MethodInvocationToNonCompiledClass() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {" +
                    "        java.util.Arrays.asList();\n" +
                    "    }\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 56, 43, 103, 4, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }
    }

    @Nested class AllowDependenciesOnInheritance {
        @Test void shouldNotReportErrorAboutSuperPackageAllowingDependencyWithoutPackageDependencies() {
            compile(
                packageInfo("source", "target"),
                file("source/sub/Source.java", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect();
        }

        @Test void shouldNotReportErrorAboutSuperPackageAllowingDependencyWithEmptyPackageDependencies() {
            compile(
                packageInfo("source", "target"),
                packageInfo("source.sub"),
                file("source/sub/Source.java", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect();
        }

        @Test void shouldNotReportErrorAboutSuperPackageAllowingDependencyWithPackageDependenciesMerge() {
            compile(
                packageInfo("source", "target1"),
                packageInfo("source.sub", "target2"),
                file("source/sub/Source.java", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target1 target1;\n" +
                    "    private Target2 target2;\n" +
                    "}\n"),

                packageInfo("target1"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "public interface Target1 {\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public interface Target2 {\n" +
                    "}\n"));

            expect();
        }

        @Test void shouldNotReportErrorAboutUnusedSuperPackageDependency() {
            compile(
                packageInfo("source", "target1"),
                packageInfo("source.sub", "target2"),
                file("source/sub/Source.java", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target2 target2;\n" +
                    "}\n"),

                packageInfo("target1"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "public interface Target1 {\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public interface Target2 {\n" +
                    "}\n"));

            expect();
        }
    }

    @Nested class IndirectDependencies {
        @Test void shouldNotReportErrorAboutAllowedIndirectDependency() {
            compile(
                packageInfo("source", "target1", "target2"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(); }\n" +
                    "}\n"),

                packageInfo("target1", "target2"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public Target2 target2() { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect();
        }

        @Test void shouldReportErrorAboutForbiddenIndirectDependency() {
            compile(
                packageInfo("source", "target1"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(); }\n" +
                    "}\n"),

                packageInfo("target1", "target2"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public Target2 target2() { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 68, 132, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenIndirectDependencyWithOverloadedPrimitiveMethods() {
            compile(
                packageInfo("source", "target1"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(1); }\n" +
                    "}\n"),

                packageInfo("target1", "target2"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public String target2(String s) { return null; }\n" +
                    "    public Target2 target2(int i) { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 68, 133, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenIndirectDependencyWithOverloadedNonPrimitiveLiteralMethods() {
            compile(
                packageInfo("source", "target1"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(\"\"); }\n" +
                    "}\n"),

                packageInfo("target1", "target2", "java.math"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public String target2(BigInteger i) { return null; }\n" +
                    "    public Target2 target2(String s) { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 68, 134, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenIndirectDependencyWithOverloadedNonPrimitiveValueMethods() {
            compile(
                packageInfo("source", "target1", "java.math"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(new BigInteger(\"123\")); }\n" +
                    "}\n"),

                packageInfo("target1", "target2", "java.math"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public String target2(String s) { return null; }\n" +
                    "    public Target2 target2(BigInteger i) { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 110, 97, 182, 7, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenMethodInvocationWithArgument() {
            compile(
                packageInfo("source", "target1"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {\n" +
                    "        new Target1().bar(1);\n" +
                    "    }\n" +
                    "}\n"),

                packageInfo("target1", "target2"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public Target2 bar(int i) { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 68, 124, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenOverloadedMethodInvocation() {
            compile(
                packageInfo("source", "target1"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {\n" +
                    "        new Target1().bar(1);\n" +
                    "    }\n" +
                    "}\n"),

                packageInfo("target1", "target2"),
                file("target1/Target1.java", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public void bar(String s) {}\n" +
                    "    public Target2 bar(int i) { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2.java", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 68, 124, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenIndirectStaticVararg0MethodInvocationToNonCompiledClass() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.Arrays;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {" +
                    "        Arrays.asList();\n" +
                    "    }\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 82, 69, 119, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenIndirectStaticVararg1MethodInvocationToNonCompiledClass() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.Arrays;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {" +
                    "        Arrays.asList((Object) \"bar\");\n" +
                    "    }\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 82, 69, 133, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenIndirectStaticVararg2MethodInvocationToNonCompiledClass() {
            compile(
                packageInfo("source"),
                file("source/Source.java", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.Arrays;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {" +
                    "        Arrays.asList((Object) \"bar\", (Object) \"baz\");\n" +
                    "    }\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 82, 69, 149, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }
    }
}
