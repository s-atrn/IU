package com.example.iu

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class IUQuickSettingsTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (IUAcceptorService.isRunning()) {
            stopAcceptor()
        } else {
            startAcceptor()
        }
    }

    private fun startAcceptor() {
        val intent =
            Intent(
                this,
                IUAcceptorService::class.java
            ).apply {
                action =
                    IUAcceptorService.ACTION_START
            }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        qsTile?.apply {
            state =
                Tile.STATE_ACTIVE

            label =
                "IU Acceptor"

            contentDescription =
                "IU Acceptor is active"

            updateTile()
        }
    }

    private fun stopAcceptor() {
        val intent =
            Intent(
                this,
                IUAcceptorService::class.java
            ).apply {
                action =
                    IUAcceptorService.ACTION_STOP
            }

        startService(intent)

        qsTile?.apply {
            state =
                Tile.STATE_INACTIVE

            label =
                "IU Acceptor"

            contentDescription =
                "IU Acceptor is inactive"

            updateTile()
        }
    }

    private fun updateTile() {
        val tile =
            qsTile
                ?: return

        if (
            IUAcceptorService.isRunning()
        ) {
            tile.state =
                Tile.STATE_ACTIVE

            tile.label =
                "IU Acceptor"

            tile.contentDescription =
                "IU Acceptor is active"
        } else {
            tile.state =
                Tile.STATE_INACTIVE

            tile.label =
                "IU Acceptor"

            tile.contentDescription =
                "IU Acceptor is inactive"
        }

        tile.icon =
            Icon.createWithResource(
                this,
                R.drawable.ic_iu_tile
            )

        tile.updateTile()
    }
}