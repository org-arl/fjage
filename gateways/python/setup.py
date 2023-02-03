from setuptools import setup, find_packages

with open('README.rst') as f:
    readme = f.read()

setup(
    name='fjagepy',
    version='1.7.4',
    description='Python Gateway',
    long_description=readme,
    author='Prasad Anjangi, Mandar Chitre, Chinmay Pendharkar, Manu Ignatius',
    author_email='prasad@subnero.com, mandar@arl.nus.edu.sg, chinmay@subnero.com, manu@subnero.com',
    url='https://github.com/org-arl/fjage/tree/master/gateways/python',
    license='BSD (3-clause)',
    python_requires='>=3',
    classifiers=[
        'Development Status :: 5 - Production/Stable',
        'Programming Language :: Python :: 3.5',
        'Programming Language :: Python :: 3.6',
        'Programming Language :: Python :: 3.7',
        'Programming Language :: Python :: 3.8',
        'Programming Language :: Python :: 3.9',
        'Programming Language :: Python :: 3.10',
        'Programming Language :: Python :: 3.11'
    ],
    packages=find_packages(),
    install_requires=[
        'numpy>=1.11'
    ],
    license_files = ('LICENSE',)
)
