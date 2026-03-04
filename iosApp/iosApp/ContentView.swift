import UIKit
import SwiftUI
import ComposeApp

struct HomeComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.HomeViewController()
        controller.view.backgroundColor = .black
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct AddonsComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.AddonsViewController()
        controller.view.backgroundColor = .black
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        TabView {
            HomeComposeView()
                .tabItem {
                    Label("Home", systemImage: "house.fill")
                }

            AddonsComposeView()
                .tabItem {
                    Label("Addons", systemImage: "puzzlepiece.extension.fill")
                }
        }
        .background(Color.black.ignoresSafeArea())
        .toolbarBackground(Color.black, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .toolbarColorScheme(.dark, for: .tabBar)
    }
}
