package com.rios;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;

public class RtcRenameAppComponent implements ApplicationComponent {

    private RtcLocalFileOperationHandler localFileOperationsHandler = new RtcLocalFileOperationHandler();

    @Override
    public void initComponent() {
        LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(
            localFileOperationsHandler);
    }

    @Override
    public void disposeComponent() {
        LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(localFileOperationsHandler);
        localFileOperationsHandler.stopDaemons();
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "RtcRenameAppComponent";
    }
}
