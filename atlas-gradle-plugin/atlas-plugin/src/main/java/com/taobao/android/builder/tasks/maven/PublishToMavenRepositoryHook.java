/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.taobao.android.builder.tasks.maven;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.publish.internal.PublishOperation;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenPublisher;
import org.gradle.api.publish.maven.internal.publisher.MavenRemotePublisher;
import org.gradle.api.publish.maven.internal.publisher.StaticLockingMavenPublisher;
import org.gradle.api.publish.maven.internal.publisher.ValidatingMavenPublisher;
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

/**
 * Publishes a {@link org.gradle.api.publish.maven.MavenPublication} to a {@link MavenArtifactRepository}.
 *
 * @since 1.4
 */
@Incubating
public class PublishToMavenRepositoryHook extends AbstractPublishToMaven {

    private MavenArtifactRepository repository;

    /**
     * The repository to publish to.
     *
     * @return The repository to publish to
     */
    public MavenArtifactRepository getRepository() {
        return repository;
    }

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to
     */
    public void setRepository(MavenArtifactRepository repository) {
        this.repository = repository;
    }

    @TaskAction
    public void publish() {

        MavenPublicationInternal publicationInternal = getPublicationInternal();
        if (publicationInternal == null) {
            throw new InvalidUserDataException("The 'publication' property is required");
        }

        MavenArtifactRepository repository = getRepository();
        if (repository == null) {
            throw new InvalidUserDataException("The 'repository' property is required");
        }

        doPublish(publicationInternal, repository);
    }

    private void doPublish(final MavenPublicationInternal publication,
                           final MavenArtifactRepository repository) {
        new PublishOperation(publication, repository.getName()) {
            @Override
            protected void publish() throws Exception {

                try {
                    MavenPublisher remotePublisher = new MavenRemotePublisher(
                            getLoggingManagerFactory(),
                            getMavenRepositoryLocator(),
                            getTemporaryDirFactory(),
                            getRepositoryTransportFactory());
                    MavenPublisher staticLockingPublisher = new StaticLockingMavenPublisher(
                            remotePublisher);
                    MavenPublisher validatingPublisher = new ValidatingMavenPublisher(
                            staticLockingPublisher);

                    validatingPublisher.publish(publication.asNormalisedPublication(), repository);
                } catch (Throwable e) {

                    String message = e.getMessage();
                    if (message.contains("Could not write to resource") &&
                            !publication.getVersion().endsWith("SNAPSHOT")) {
                        throw new GradleException("无法deploy release版本，请通过mtl平台打包发布",e);
                    }

                    if (message.contains("status code 400") &&
                            !publication.getVersion().endsWith("SNAPSHOT")) {
                        throw new GradleException("您发布的版本 " +
                                                          publication.getVersion() +
                                                          " 在仓库里已经存在",e);
                    }

                    if (message.contains("status code 413")) {
                        throw new GradleException("您发布超过200M，无法上传",e);
                    }

                    throw e;
                }
            }
        }.run();
    }

    @Inject
    protected RepositoryTransportFactory getRepositoryTransportFactory() {
        throw new UnsupportedOperationException();
    }
}
