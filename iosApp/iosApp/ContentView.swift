import UIKit
import SwiftUI
import ComposeApp

struct HomeComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.HomeViewController()
        controller.view.backgroundColor = UIColor(red: 0.008, green: 0.016, blue: 0.016, alpha: 1.0)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct AddonsComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.AddonsViewController()
        controller.view.backgroundColor = UIColor(red: 0.008, green: 0.016, blue: 0.016, alpha: 1.0)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        TabView {
            HomeComposeView()
                .ignoresSafeArea()
                .tabItem {
                    Label("Home", systemImage: "house.fill")
                }

            AddonsComposeView()
                .ignoresSafeArea()
                .tabItem {
                    Label("Addons", systemImage: "puzzlepiece.extension.fill")
                }
        }
        .background(Color(red: 0.008, green: 0.016, blue: 0.016).ignoresSafeArea())
        .toolbarBackground(Color(red: 0.039, green: 0.051, blue: 0.051), for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .toolbarColorScheme(.dark, for: .tabBar)
    }
}
